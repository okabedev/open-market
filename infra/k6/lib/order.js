// 주문 → 결제 흐름의 단계(장애 주입 시나리오 saga.js, coupon-saga.js가 쓴다). 사용자처럼 끝까지 해 보고 막히면 null·false를 준다.
// 실패한 요청은 같은 멱등 키로 다시 부른다(BFF와 같다).
import http from "k6/http";
import { sleep } from "k6";
import { env } from "./env.js";
import { uuid } from "./auth.js";

const FAKE_PG = __ENV.FAKE_PG_URL || "http://fake-pg:8090";
// 결제 준비를 기다리는 최대 시간. Kafka를 1분 멈춰도 그 뒤에 따라잡을 만큼 둔다.
export const READY_TIMEOUT_SECONDS = 180;
const ATTEMPTS = 4;
// 주문 생성은 DB가 느리면 gateway 타임아웃(10초) 뒤에도 서버에서 계속 처리된다. 같은 키로 다시 보내면 처리가 끝날 때까지
// 409 COMMON_IDEMPOTENCY_IN_PROGRESS이고, 끝나면 처음 응답(201)이 온다. 사용자가 다시 누르는 것처럼 더 오래 다시 부른다.
const CREATE_ATTEMPTS = 15;

export function authHeaders(token) {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

/**
 * 주문을 만든다. 5xx·연결 실패, 처리 중(409)이면 같은 멱등 키로 다시 부른다.
 * @returns {{ orderId?: string, status: number, body: string }} 201이면 orderId가 있다. 그 밖의 거절은 status와 body로 판단한다
 */
export function createOrder(headers, skuId, price, issuedCouponIds = []) {
  const key = uuid();
  const payload = {
    lines: [{ skuId, quantity: 1, expectedPrice: price }],
    shipping: { recipientName: "장애주입", recipientPhone: "010-1234-5678", zipCode: "06236", baseAddress: "서울 강남구 테헤란로 1", detailAddress: "1층" },
  };
  if (issuedCouponIds.length > 0) {
    payload.issuedCouponIds = issuedCouponIds;
  }
  const body = JSON.stringify(payload);
  let res;
  for (let attempt = 0; attempt < CREATE_ATTEMPTS; attempt++) {
    res = http.post(`${env.gateway}/api/order/v1/orders`, body, {
      headers: { ...headers, "Idempotency-Key": key },
      tags: { name: "order-create" },
    });
    if (res.status === 201) {
      return { orderId: res.json("orderId"), status: 201, body: "" };
    }
    const inProgress = res.status === 409 && String(res.body).includes("COMMON_IDEMPOTENCY_IN_PROGRESS");
    if (res.status !== 0 && res.status < 500 && !inProgress) {
      break;
    }
    sleep(3);
  }
  return { status: res.status, body: String(res.body).slice(0, 300) };
}

/** 결제가 준비될 때까지(READY) 기다린다. 결제는 OrderAwaitingPayment를 받아 만들어진다. 시간 안에 안 되면 null. */
export function waitForPayment(orderId, headers, seconds = READY_TIMEOUT_SECONDS) {
  const deadline = Date.now() + seconds * 1000;
  while (Date.now() < deadline) {
    const res = http.get(`${env.gateway}/api/payment/v1/payments/orders/${orderId}`, {
      headers,
      tags: { name: "payment-ready" },
      // 결제 준비 전에는 404다. 실패로 세지 않는다.
      responseCallback: http.expectedStatuses(200, 404),
    });
    if (res.status === 200 && res.json("status") === "READY") {
      return { amount: res.json("amount") };
    }
    sleep(2);
  }
  return null;
}

/** 주문 상태. 예약 실패로 취소되면 결제가 생기지 않으므로 주문을 본다. */
export function orderStatus(orderId, headers) {
  const res = http.get(`${env.gateway}/api/order/v1/orders/${orderId}`, { headers, tags: { name: "order-get" } });
  return res.status === 200 ? { status: res.json("status"), cancelReason: res.json("cancelReason") } : null;
}

/** 가짜 PG의 승인 응답을 늦춘다(이 주문만). */
export function delayPgConfirm(orderId, ms) {
  http.put(`${FAKE_PG}/control/orders/${orderId}`, JSON.stringify({ confirmDelayMs: ms }), {
    headers: { "Content-Type": "application/json" },
    tags: { name: "pg-control" },
  });
}

/** 가짜 PG의 결제 인증(브라우저가 승인 버튼을 누른 것과 같다). 결제 키는 successUrl의 쿼리로 온다. */
export function approveAtPg(orderId, amount) {
  const res = http.post(
    `${FAKE_PG}/checkout/approve`,
    {
      orderId,
      amount: String(amount),
      orderName: "장애 주입 주문",
      successUrl: "http://shop.local/pay/confirm",
      failUrl: "http://shop.local/pay",
    },
    { redirects: 0, tags: { name: "pg-approve" } },
  );
  const match = /[?&]paymentKey=([^&]+)/.exec(res.headers.Location || "");
  return match ? decodeURIComponent(match[1]) : null;
}

/** 결제 승인. 200(정해짐)이나 202(IN_PROGRESS, 재조회 작업이 정한다)면 사용자 쪽 일은 끝났다. 5xx·연결 실패·처리 중이면 같은 키로 다시 부른다. */
export function confirm(orderId, paymentKey, amount, headers) {
  const body = JSON.stringify({ paymentKey, orderId, amount });
  for (let attempt = 0; attempt < ATTEMPTS; attempt++) {
    const res = http.post(`${env.gateway}/api/payment/v1/payments/confirm`, body, {
      headers: { ...headers, "Idempotency-Key": `confirm-${paymentKey}` },
      tags: { name: "payment-confirm" },
    });
    if (res.status === 200 || res.status === 202) {
      return true;
    }
    if (res.status !== 0 && res.status < 500 && res.status !== 409) {
      console.warn(`결제 승인 거절: ${res.status} ${String(res.body).slice(0, 200)}`);
      return false;
    }
    sleep(3);
  }
  return false;
}

/** 결제 준비를 기다려 가짜 PG 승인과 결제 승인까지 한다. 결과: "paid", "not-ready", "approve-failed", "confirm-failed". */
export function pay(orderId, headers, pgDelayMs = 0, readySeconds = READY_TIMEOUT_SECONDS) {
  const payment = waitForPayment(orderId, headers, readySeconds);
  if (!payment) {
    return "not-ready";
  }
  if (pgDelayMs > 0) {
    delayPgConfirm(orderId, pgDelayMs);
  }
  const paymentKey = approveAtPg(orderId, payment.amount);
  if (!paymentKey) {
    return "approve-failed";
  }
  return confirm(orderId, paymentKey, payment.amount, headers) ? "paid" : "confirm-failed";
}
