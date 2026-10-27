// 장애 주입(R16)의 Redis 장애 중 선착순 쿠폰 발급(ADR 0020에서 서비스를 모두 띄워 보지 않은 것). infra/fault/run.sh redis가 돌린다.
// 구매자가 초당 ISSUE_RATE명씩 들어와 쿠폰을 받는다. 그 사이 run.sh가 Redis를 멈췄다가 다시 띄운다.
// Redis가 없으면 발급은 503 PROMOTION_UNAVAILABLE이고(DB만으로 발급하지 않는다), 구매자는 1초 뒤 다시 누른다.
// 수량은 구매자 수보다 많게 둬 발급이 장애 내내 이어지게 한다. 초과 발급·1인 2매가 없는지는 check.sql이 본다.
import http from "k6/http";
import { sleep } from "k6";
import { Counter } from "k6/metrics";
import exec from "k6/execution";
import { env, runTag } from "./lib/env.js";
import { adminToken, login, signUp, uuid } from "./lib/auth.js";
import { api } from "./lib/seed.js";

const RATE = Number(__ENV.ISSUE_RATE || 3);
const DURATION = __ENV.ISSUE_DURATION || "3m";
const BUYERS = Math.ceil(RATE * 180) + 20;
const QUANTITY = BUYERS + 100;
// 한 구매자가 503을 받고 다시 누르는 최대 시간
const RETRY_SECONDS = 120;

const issued = new Counter("issue_issued");
const soldOut = new Counter("issue_sold_out");
const unavailable = new Counter("issue_unavailable_responses");
const gaveUp = new Counter("issue_gave_up");
const other = new Counter("issue_other");

export const options = {
  setupTimeout: "6m",
  scenarios: {
    issue: {
      executor: "constant-arrival-rate",
      exec: "issue",
      rate: RATE,
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: Math.min(RATE * (RETRY_SECONDS + 10), 1000),
      maxVUs: 1000,
      gracefulStop: `${RETRY_SECONDS + 10}s`,
    },
  },
  thresholds: { issue_other: ["count==0"] },
};

export function setup() {
  const tag = runTag();
  const now = Date.now();
  const couponId = api("POST", "/api/promotion/v1/admin/coupons", adminToken(), {
    name: `장애 주입 발급 쿠폰 ${tag}`,
    discountType: "FIXED",
    discountValue: 1000,
    minOrderAmount: 0,
    quantity: QUANTITY,
    issueStartAt: new Date(now - 60_000).toISOString(),
    issueEndAt: new Date(now + 3_600_000).toISOString(),
    validDays: 7,
  }, { headers: { "Idempotency-Key": uuid() }, expect: [201] }).json("couponId");
  const buyers = signUp(tag, "issue", BUYERS, 50_000);
  return { couponId, tokens: login("shop", buyers) };
}

export function issue(data) {
  const token = data.tokens[exec.scenario.iterationInTest % data.tokens.length];
  const deadline = Date.now() + RETRY_SECONDS * 1000;
  while (Date.now() < deadline) {
    const res = http.post(`${env.gateway}/api/promotion/v1/coupons/${data.couponId}/issue`, null, {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: "coupon-issue" },
      responseCallback: http.expectedStatuses(200, 201, 409, 503),
    });
    if (res.status === 200 || res.status === 201) {
      issued.add(1);
      return;
    }
    if (res.status === 409 && String(res.body).includes("PROMOTION_COUPON_SOLD_OUT")) {
      soldOut.add(1);
      return;
    }
    if (res.status === 503 && String(res.body).includes("PROMOTION_UNAVAILABLE")) {
      unavailable.add(1);
      sleep(1);
      continue;
    }
    console.warn(`발급 응답: ${res.status} ${String(res.body).slice(0, 200)}`);
    other.add(1, { status: String(res.status) });
    return;
  }
  gaveUp.add(1);
}
