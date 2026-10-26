// 선착순 쿠폰(NFR-9: 1,000건 동시 요청에서 초과 발급 0건). 수량 100장 쿠폰에 구매자 1,000명이 한꺼번에 받기를 요청한다.
// 요청은 가상 사용자 하나가 http.batch로 동시에 보낸다. 가상 사용자 1,000개를 띄우는 것보다 부하 발생기 메모리가 작다(R13).
// 발급 성공(200·201) 수, 품절(409) 수, 쿠폰 조회의 DB 발급 수(issuedCount)가 모두 수량과 맞아야 한다.
import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";
import { env, runTag } from "./lib/env.js";
import { adminToken, login, signUp, uuid } from "./lib/auth.js";
import { api } from "./lib/seed.js";

const BUYERS = Number(__ENV.COUPON_BUYERS || 1000);
const QUANTITY = Number(__ENV.COUPON_QUANTITY || 100);

const issued = new Counter("coupon_issued");
const soldOut = new Counter("coupon_sold_out");
const other = new Counter("coupon_other");

export const options = {
  setupTimeout: "8m",
  scenarios: { issue: { executor: "shared-iterations", vus: 1, iterations: 1, maxDuration: "2m" } },
  batch: BUYERS,
  batchPerHost: BUYERS,
  thresholds: {
    coupon_issued: [`count==${QUANTITY}`],
    coupon_sold_out: [`count==${BUYERS - QUANTITY}`],
    coupon_other: ["count==0"],
    // 판정에 쓰지 않는다. 요약에 발급 요청의 지연을 따로 보이게 한다.
    "http_req_duration{name:coupon-issue}": ["max>=0"],
    checks: ["rate==1"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const tag = runTag();
  const now = Date.now();
  const couponId = api("POST", "/api/promotion/v1/admin/coupons", adminToken(), {
    name: `부하 시험 쿠폰 ${tag}`,
    discountType: "FIXED",
    discountValue: 1000,
    minOrderAmount: 0,
    quantity: QUANTITY,
    issueStartAt: new Date(now - 60_000).toISOString(),
    issueEndAt: new Date(now + 3_600_000).toISOString(),
    validDays: 7,
  }, { headers: { "Idempotency-Key": uuid() }, expect: [201] }).json("couponId");
  const buyers = signUp(tag, "coupon", BUYERS, 40_000);
  return { couponId, tokens: login("shop", buyers) };
}

export default function (data) {
  const responses = http.batch(
    data.tokens.map((token) => ({
      method: "POST",
      url: `${env.gateway}/api/promotion/v1/coupons/${data.couponId}/issue`,
      params: { headers: { Authorization: `Bearer ${token}` }, tags: { name: "coupon-issue" } },
    })),
  );
  responses.forEach((r) => {
    if (r.status === 200 || r.status === 201) {
      issued.add(1);
    } else if (r.status === 409 && String(r.body).includes("PROMOTION_COUPON_SOLD_OUT")) {
      soldOut.add(1);
    } else {
      other.add(1);
    }
  });
  const coupon = http.get(`${env.gateway}/api/promotion/v1/coupons/${data.couponId}`, { tags: { name: "seed" } });
  check(coupon, { "DB 발급 수가 수량과 같다": (r) => r.status === 200 && r.json("issuedCount") === QUANTITY });
}
