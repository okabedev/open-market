// 장애 주입(R16)의 쿠폰 예약 실패 조합(ADR 0021에서 서비스를 모두 띄워 보지 않은 것). infra/fault/run.sh coupon이 돌린다.
// 주문 생성은 할인을 동기로 계산하고 재고·쿠폰 예약은 이벤트로 따로 하므로, 주문 두 개를 한꺼번에 넣으면 둘 다 생성을 지나
// 예약에서 갈린다. 경우마다 주문 두 개를 동시에 넣고, 결제 대기가 된 주문은 끝까지 결제한다.
//
// - coupon: 한 구매자가 같은 쿠폰으로 재고가 넉넉한 상품을 두 번 주문한다 → 쿠폰 예약이 하나만 된다
// - stock: 두 구매자가 각자 쿠폰으로 재고 1개 상품을 주문한다 → 재고 예약이 하나만 된다. 진 쪽 쿠폰은 풀린다
// - both: 한 구매자가 같은 쿠폰으로 재고 1개 상품을 두 번 주문한다 → 재고와 쿠폰을 서로 다른 주문이 잡으면 둘 다 취소된다
//
// 두 주문이 모두 결제되면(쿠폰 두 번 사용, 재고 초과 판매) 결함이다(combo_double_paid). 데이터 정합성은 check.sql이 본다.
import http from "k6/http";
import { sleep } from "k6";
import { Counter } from "k6/metrics";
import exec from "k6/execution";
import { env, runTag } from "./lib/env.js";
import { adminToken, login, signUp, uuid, verifyEmails } from "./lib/auth.js";
import { api, approvedSeller, products } from "./lib/seed.js";
import { authHeaders, orderStatus, pay } from "./lib/order.js";

const CASES = Number(__ENV.COMBO_CASES || 30);
const KINDS = ["coupon", "stock", "both"];
// infra/fault/run.sh의 INITIAL_STOCK과 같다. 재고 1개 상품도 이 재고로 만든 뒤 조정으로 줄인다(check.sql의 재고 차감이 맞게).
const INITIAL_STOCK = 100_000;

const paidOrders = new Counter("combo_orders_paid");
const cancelled = new Counter("combo_orders_cancelled");
const rejected = new Counter("combo_orders_rejected");
const stuck = new Counter("combo_orders_not_final");
const doublePaid = new Counter("combo_double_paid");
const winners = { 0: new Counter("combo_cases_no_winner"), 1: new Counter("combo_cases_one_winner") };

export const options = {
  setupTimeout: "8m",
  scenarios: {
    combo: { executor: "shared-iterations", exec: "combo", vus: 10, iterations: CASES, maxDuration: "6m" },
  },
  thresholds: { combo_double_paid: ["count==0"] },
};

export function setup() {
  const tag = runTag();
  const now = Date.now();
  const couponId = api("POST", "/api/promotion/v1/admin/coupons", adminToken(), {
    name: `장애 주입 쿠폰 ${tag}`,
    discountType: "FIXED",
    discountValue: 1000,
    minOrderAmount: 0,
    quantity: CASES * 2 + 10,
    issueStartAt: new Date(now - 60_000).toISOString(),
    issueEndAt: new Date(now + 3_600_000).toISOString(),
    validDays: 7,
  }, { headers: { "Idempotency-Key": uuid() }, expect: [201] }).json("couponId");

  const seller = approvedSeller(tag, 40_000);
  const [plenty] = products(seller, ["쿠폰 조합 상품"], INITIAL_STOCK);
  // stock·both 경우마다 재고 1개 상품
  const scarceCount = Math.ceil((CASES * 2) / 3);
  const scarce = products(seller, Array.from({ length: scarceCount }, (_, i) => `쿠폰 조합 한정 상품 ${i + 1}`), INITIAL_STOCK);
  for (const item of scarce) {
    api("POST", `/api/catalog/v1/seller/products/${item.productId}/skus/${item.skuId}/stock-adjustments`, seller.token,
      { delta: 1 - INITIAL_STOCK, reason: "장애 주입: 재고 1개" }, { headers: { "Idempotency-Key": uuid() }, expect: [201] });
  }

  // 경우마다 구매자 둘(coupon·both는 한 명만 쓴다). 각자 쿠폰을 받는다.
  const buyers = signUp(tag, "combo", CASES * 2, 41_000);
  verifyEmails(buyers);
  const tokens = login("shop", buyers);
  const coupons = tokens.map((token) =>
    api("POST", `/api/promotion/v1/coupons/${couponId}/issue`, token, null, { expect: [200, 201] }).json("issuedCouponId"));
  return { plenty, scarce, tokens, coupons };
}

export function combo(data) {
  const n = exec.scenario.iterationInTest;
  const kind = KINDS[n % KINDS.length];
  const x = { headers: authHeaders(data.tokens[n * 2]), coupon: data.coupons[n * 2] };
  const y = { headers: authHeaders(data.tokens[n * 2 + 1]), coupon: data.coupons[n * 2 + 1] };
  const scarce = data.scarce[Math.floor((n * 2) / 3) % data.scarce.length];
  const requests = {
    coupon: [[x, data.plenty], [x, data.plenty]],
    stock: [[x, scarce], [y, scarce]],
    both: [[x, scarce], [x, scarce]],
  }[kind];

  // 두 주문을 한꺼번에 넣는다.
  const responses = http.batch(requests.map(([buyer, item]) => ({
    method: "POST",
    url: `${env.gateway}/api/order/v1/orders`,
    body: JSON.stringify({
      lines: [{ skuId: item.skuId, quantity: 1, expectedPrice: item.price }],
      issuedCouponIds: [buyer.coupon],
      shipping: { recipientName: "장애주입", recipientPhone: "010-1234-5678", zipCode: "06236", baseAddress: "서울 강남구 테헤란로 1", detailAddress: "1층" },
    }),
    params: { headers: { ...buyer.headers, "Idempotency-Key": uuid() }, tags: { name: "order-create" } },
  })));

  let paidInCase = 0;
  responses.forEach((res, i) => {
    if (res.status !== 201) {
      // 앞 주문의 예약이 먼저 끝나 생성에서 걸렸다(쿠폰 사용 불가 422, 재고 부족 409). 설계대로다.
      rejected.add(1, { kind, status: String(res.status) });
      return;
    }
    const orderId = res.json("orderId");
    const headers = requests[i][0].headers;
    const state = waitUntilDecided(orderId, headers);
    if (state === "AWAITING_PAYMENT") {
      if (pay(orderId, headers, 0, 60) === "paid") {
        paidOrders.add(1, { kind });
        paidInCase += 1;
      }
    } else if (state === "CANCELLED") {
      cancelled.add(1, { kind });
    } else {
      stuck.add(1, { kind });
    }
  });
  if (paidInCase > 1) {
    console.error(`두 주문이 모두 결제됐다(${kind}): ${responses.map((r) => r.body).join(" / ")}`);
    doublePaid.add(1, { kind });
  } else {
    winners[paidInCase].add(1, { kind });
  }
}

/** 예약 결과로 주문 상태가 정해질 때까지(결제 대기 또는 취소) 기다린다. */
function waitUntilDecided(orderId, headers) {
  for (let i = 0; i < 60; i++) {
    const order = orderStatus(orderId, headers);
    if (order && order.status !== "PENDING") {
      return order.status;
    }
    sleep(1);
  }
  return "PENDING";
}
