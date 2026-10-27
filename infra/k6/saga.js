// 장애 주입(R16)의 주문·결제 흐름. infra/fault/run.sh가 장애를 넣고 빼는 동안 돈다. 판정은 이 스크립트가 아니라
// 끝난 뒤 DB를 세는 infra/fault/check.sql이 한다. 여기서는 사용자처럼 끝까지 해 보고 무엇이 막혔는지만 센다.
//
// 반복 하나: 주문 생성 → 결제 준비(OrderAwaitingPayment → payment가 READY 결제를 만든다)를 기다림 → 가짜 PG 승인 화면 대신
// /checkout/approve로 결제 키를 받음 → 결제 승인 API. 실패하면 같은 멱등 키로 몇 번 다시 부른다(BFF와 같다).
// 장애 중에는 반복 하나가 몇 분 걸릴 수 있어 가상 사용자를 넉넉히 둔다. 액세스 토큰 수명(10분) 안에 끝나야 한다.
import http from "k6/http";
import { sleep } from "k6";
import { Counter } from "k6/metrics";
import exec from "k6/execution";
import { env, runTag } from "./lib/env.js";
import { login, signUp, uuid, verifyEmails } from "./lib/auth.js";
import { approvedSeller, products } from "./lib/seed.js";

const BUYERS = Number(__ENV.SAGA_BUYERS || 30);
const RATE = Number(__ENV.SAGA_RATE || 2);
const DURATION = __ENV.SAGA_DURATION || "3m";
// SAGA_PG_DELAY_EVERY번째 주문마다 가짜 PG의 승인 응답을 SAGA_PG_DELAY_MS만큼 늦춘다(0이면 늦추지 않는다).
const PG_DELAY_MS = Number(__ENV.SAGA_PG_DELAY_MS || 0);
const PG_DELAY_EVERY = Number(__ENV.SAGA_PG_DELAY_EVERY || 0);
const FAKE_PG = __ENV.FAKE_PG_URL || "http://fake-pg:8090";
// 결제 준비를 기다리는 최대 시간. Kafka를 1분 멈춰도 그 뒤에 따라잡을 만큼 둔다.
const READY_TIMEOUT_SECONDS = 180;
const ATTEMPTS = 4;
// 주문 생성은 DB가 느리면 gateway 타임아웃(10초) 뒤에도 서버에서 계속 처리된다. 같은 키로 다시 보내면 처리가 끝날 때까지
// 409 COMMON_IDEMPOTENCY_IN_PROGRESS이고, 끝나면 처음 응답(201)이 온다. 사용자가 다시 누르는 것처럼 더 오래 다시 부른다.
const CREATE_ATTEMPTS = 15;

const created = new Counter("saga_orders_created");
const createFailed = new Counter("saga_order_create_failed");
const notReady = new Counter("saga_payment_not_ready");
const approveFailed = new Counter("saga_pg_approve_failed");
const confirmed = new Counter("saga_payments_confirmed");
const confirmFailed = new Counter("saga_payment_confirm_failed");

export const options = {
  setupTimeout: "6m",
  scenarios: {
    saga: {
      executor: "constant-arrival-rate",
      exec: "saga",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      // 장애 중에는 반복 하나가 몇 분 걸린다. 가상 사용자를 실행 중에 늘리면 늦어 반복을 버리므로 미리 만든다.
      preAllocatedVUs: Math.min(RATE * 200, 1000),
      maxVUs: 1000,
      // 마지막 주문도 결제 준비를 기다려 끝낸다.
      gracefulStop: `${READY_TIMEOUT_SECONDS + 60}s`,
    },
  },
  summaryTrendStats: ["avg", "med", "p(95)", "max"],
};

export function setup() {
  const tag = runTag();
  const seller = approvedSeller(tag, 30_000);
  const names = Array.from({ length: 5 }, (_, i) => `장애 주입 상품 ${i + 1}`);
  const items = products(seller, names, 100_000);
  const buyers = signUp(tag, "saga", BUYERS, 31_000);
  verifyEmails(buyers);
  return { items, tokens: login("shop", buyers) };
}

export function saga(data) {
  const i = exec.scenario.iterationInTest;
  const token = data.tokens[i % data.tokens.length];
  const item = data.items[i % data.items.length];
  const auth = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };

  const orderId = createOrder(item, auth);
  if (!orderId) {
    createFailed.add(1);
    return;
  }
  created.add(1);

  const payment = waitForPayment(orderId, auth);
  if (!payment) {
    // 결제 기한이 지나면 주문은 만료로 취소된다. check.sql이 끝 상태를 본다.
    notReady.add(1);
    return;
  }

  if (PG_DELAY_EVERY > 0 && i % PG_DELAY_EVERY === 0) {
    http.put(`${FAKE_PG}/control/orders/${orderId}`, JSON.stringify({ confirmDelayMs: PG_DELAY_MS }), {
      headers: { "Content-Type": "application/json" },
      tags: { name: "pg-control" },
    });
  }
  const paymentKey = approveAtPg(orderId, payment.amount);
  if (!paymentKey) {
    approveFailed.add(1);
    return;
  }
  if (confirm(orderId, paymentKey, payment.amount, auth)) {
    confirmed.add(1);
  } else {
    confirmFailed.add(1);
  }
}

/** 주문을 만든다. 5xx·연결 실패, 처리 중(409)이면 같은 멱등 키로 다시 부른다. */
function createOrder(item, headers) {
  const key = uuid();
  const body = JSON.stringify({
    lines: [{ skuId: item.skuId, quantity: 1, expectedPrice: item.price }],
    shipping: { recipientName: "장애주입", recipientPhone: "010-1234-5678", zipCode: "06236", baseAddress: "서울 강남구 테헤란로 1", detailAddress: "1층" },
  });
  for (let attempt = 0; attempt < CREATE_ATTEMPTS; attempt++) {
    const res = http.post(`${env.gateway}/api/order/v1/orders`, body, {
      headers: { ...headers, "Idempotency-Key": key },
      tags: { name: "order-create" },
    });
    if (res.status === 201) {
      return res.json("orderId");
    }
    const inProgress = res.status === 409 && String(res.body).includes("COMMON_IDEMPOTENCY_IN_PROGRESS");
    if (res.status !== 0 && res.status < 500 && !inProgress) {
      console.warn(`주문 생성 거절: ${res.status} ${String(res.body).slice(0, 200)}`);
      return null;
    }
    sleep(3);
  }
  return null;
}

/** 결제가 준비될 때까지(READY) 기다린다. 결제는 OrderAwaitingPayment를 받아 만들어진다. */
function waitForPayment(orderId, headers) {
  const deadline = Date.now() + READY_TIMEOUT_SECONDS * 1000;
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

/** 가짜 PG의 결제 인증(브라우저가 승인 버튼을 누른 것과 같다). 결제 키는 successUrl의 쿼리로 온다. */
function approveAtPg(orderId, amount) {
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
  const location = res.headers.Location || "";
  const match = /[?&]paymentKey=([^&]+)/.exec(location);
  return match ? decodeURIComponent(match[1]) : null;
}

/** 결제 승인. 200(정해짐)이나 202(IN_PROGRESS, 재조회 작업이 정한다)면 사용자 쪽 일은 끝났다. 5xx·연결 실패면 같은 키로 다시 부른다. */
function confirm(orderId, paymentKey, amount, headers) {
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
