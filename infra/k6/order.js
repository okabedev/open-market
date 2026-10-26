// 주문 생성(NFR-9: p95 500ms 이하, PG 호출 제외). 이메일을 인증한 구매자들이 상품 하나씩 주문한다.
// 주문 생성 API는 catalog를 동기로 읽고 주문과 OrderPlaced를 저장한다. 결제는 하지 않으므로 주문은 결제 기한 뒤 만료된다.
// 액세스 토큰 수명이 10분이라 setup과 실행을 합쳐 10분 안에 끝나야 한다.
import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { env, phased, runTag } from "./lib/env.js";
import { login, signUp, uuid, verifyEmails } from "./lib/auth.js";
import { approvedSeller, products } from "./lib/seed.js";

const BUYERS = Number(__ENV.ORDER_BUYERS || 50);
const RATE = Number(__ENV.ORDER_RATE || 10);
const DURATION = __ENV.ORDER_DURATION || "2m";

export const options = {
  setupTimeout: "6m",
  scenarios: phased({ order: { exec: "order", rate: RATE, preAllocatedVUs: 20, maxVUs: 200 } }, DURATION),
  thresholds: {
    "http_req_duration{name:order-create,phase:measure}": ["p(95)<500"],
    "http_req_failed{name:order-create,phase:measure}": ["rate<0.01"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const tag = runTag();
  const seller = approvedSeller(tag, 20_000);
  // 한 상품에 몰리지 않게 열 개로 나눈다. 재고는 실행 중 모자라지 않을 만큼 둔다.
  const names = Array.from({ length: 10 }, (_, i) => `주문 부하 상품 ${i + 1}`);
  const items = products(seller, names, 100_000);
  const buyers = signUp(tag, "buyer", BUYERS, 21_000);
  verifyEmails(buyers);
  return { items, tokens: login("shop", buyers) };
}

export function order(data) {
  const i = exec.scenario.iterationInTest;
  const item = data.items[i % data.items.length];
  const res = http.post(
    `${env.gateway}/api/order/v1/orders`,
    JSON.stringify({
      lines: [{ skuId: item.skuId, quantity: 1, expectedPrice: item.price }],
      shipping: { recipientName: "부하시험", recipientPhone: "010-1234-5678", zipCode: "06236", baseAddress: "서울 강남구 테헤란로 1", detailAddress: "1층" },
    }),
    {
      headers: {
        Authorization: `Bearer ${data.tokens[i % data.tokens.length]}`,
        "Content-Type": "application/json",
        "Idempotency-Key": uuid(),
      },
      tags: { name: "order-create" },
    },
  );
  check(res, { "주문 201": (r) => r.status === 201 });
}
