// 장애 주입(R16)의 주문·결제 흐름. infra/fault/run.sh가 장애를 넣고 빼는 동안 돈다. 판정은 이 스크립트가 아니라
// 끝난 뒤 DB를 세는 infra/fault/check.sql이 한다. 여기서는 사용자처럼 끝까지 해 보고 무엇이 막혔는지만 센다.
//
// 반복 하나: 주문 생성 → 결제 준비를 기다림 → 가짜 PG 승인 → 결제 승인(infra/k6/lib/order.js).
// 장애 중에는 반복 하나가 몇 분 걸릴 수 있어 가상 사용자를 넉넉히 둔다. 액세스 토큰 수명(10분) 안에 끝나야 한다.
import { Counter } from "k6/metrics";
import exec from "k6/execution";
import { runTag } from "./lib/env.js";
import { login, signUp, verifyEmails } from "./lib/auth.js";
import { approvedSeller, products } from "./lib/seed.js";
import { READY_TIMEOUT_SECONDS, authHeaders, createOrder, pay } from "./lib/order.js";

const BUYERS = Number(__ENV.SAGA_BUYERS || 30);
const RATE = Number(__ENV.SAGA_RATE || 2);
const DURATION = __ENV.SAGA_DURATION || "3m";
// SAGA_PG_DELAY_EVERY번째 주문마다 가짜 PG의 승인 응답을 SAGA_PG_DELAY_MS만큼 늦춘다(0이면 늦추지 않는다).
const PG_DELAY_MS = Number(__ENV.SAGA_PG_DELAY_MS || 0);
const PG_DELAY_EVERY = Number(__ENV.SAGA_PG_DELAY_EVERY || 0);
// infra/fault/run.sh의 INITIAL_STOCK과 같다(check.sql이 재고 차감을 맞춰 본다).
const INITIAL_STOCK = 100_000;

const created = new Counter("saga_orders_created");
const createFailed = new Counter("saga_order_create_failed");
const outcomes = {
  "paid": new Counter("saga_payments_confirmed"),
  "not-ready": new Counter("saga_payment_not_ready"),
  "approve-failed": new Counter("saga_pg_approve_failed"),
  "confirm-failed": new Counter("saga_payment_confirm_failed"),
};

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
  const items = products(seller, names, INITIAL_STOCK);
  const buyers = signUp(tag, "saga", BUYERS, 31_000);
  verifyEmails(buyers);
  return { items, tokens: login("shop", buyers) };
}

export function saga(data) {
  const i = exec.scenario.iterationInTest;
  const headers = authHeaders(data.tokens[i % data.tokens.length]);
  const item = data.items[i % data.items.length];

  const order = createOrder(headers, item.skuId, item.price);
  if (!order.orderId) {
    console.warn(`주문 생성 거절: ${order.status} ${order.body}`);
    createFailed.add(1);
    return;
  }
  created.add(1);
  const delay = PG_DELAY_EVERY > 0 && i % PG_DELAY_EVERY === 0 ? PG_DELAY_MS : 0;
  // 결제하지 못한 주문은 결제 기한 뒤 만료로 취소된다. check.sql이 끝 상태를 본다.
  outcomes[pay(order.orderId, headers, delay)].add(1);
}
