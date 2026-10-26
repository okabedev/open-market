// 상품 조회와 검색(NFR-9: p95 200ms 이하). 로그인하지 않은 구매자가 상품 상세와 검색을 부른다.
// 요청마다 가상의 클라이언트 주소를 바꿔 익명 레이트 리밋(IP마다 초당 20회)이 아니라 서비스 지연을 잰다(R13).
import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { env, clientAddress, phased, runTag } from "./lib/env.js";
import { approvedSeller, products, waitForSearch } from "./lib/seed.js";

const FRUITS = ["사과", "배", "포도", "복숭아", "자두", "감귤", "딸기", "참외", "수박", "블루베리"];
const KINDS = ["주스", "잼", "말랭이", "선물세트"];
const RATE = Number(__ENV.BROWSE_RATE || 100);
const DURATION = __ENV.BROWSE_DURATION || "2m";

export const options = {
  setupTimeout: "5m",
  // 상세 3 : 검색 1. 일정한 도착률로 보내 서버가 느려져도 요청 수가 줄지 않게 한다.
  // 기동 직후에는 JIT 컴파일과 연결 생성으로 지연이 크다. 예열 단계를 먼저 돌리고 임계값은 측정 단계(phase:measure)에만 건다.
  scenarios: phased({
    detail: { exec: "detail", rate: Math.round((RATE * 3) / 4), preAllocatedVUs: 50, maxVUs: 300 },
    search: { exec: "search", rate: Math.round(RATE / 4), preAllocatedVUs: 20, maxVUs: 150 },
  }, DURATION),
  thresholds: {
    "http_req_duration{name:product-detail,phase:measure}": ["p(95)<200"],
    "http_req_duration{name:search,phase:measure}": ["p(95)<200"],
    "http_req_failed{name:product-detail,phase:measure}": ["rate<0.01"],
    "http_req_failed{name:search,phase:measure}": ["rate<0.01"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const tag = runTag();
  const seller = approvedSeller(tag, 1);
  const names = [];
  FRUITS.forEach((fruit) => KINDS.forEach((kind) => names.push(`${fruit} ${kind}`)));
  const created = products(seller, names, 1000);
  waitForSearch("선물세트", FRUITS.length);
  return { productIds: created.map((p) => p.productId) };
}

function headers() {
  return { "X-Forwarded-For": clientAddress(1000 + exec.scenario.iterationInTest) };
}

export function detail(data) {
  const id = data.productIds[exec.scenario.iterationInTest % data.productIds.length];
  const res = http.get(`${env.gateway}/api/catalog/v1/products/${id}`, { headers: headers(), tags: { name: "product-detail" } });
  check(res, { "상품 상세 200": (r) => r.status === 200 });
}

export function search() {
  const i = exec.scenario.iterationInTest;
  const q = i % 2 === 0 ? FRUITS[i % FRUITS.length] : `${FRUITS[i % FRUITS.length]} ${KINDS[i % KINDS.length]}`;
  const sort = ["RELEVANCE", "LATEST", "PRICE_ASC", "RATING"][i % 4];
  const res = http.get(`${env.gateway}/api/search/v1/products?q=${encodeURIComponent(q)}&sort=${sort}&size=20`, {
    headers: headers(),
    tags: { name: "search" },
  });
  check(res, { "검색 200": (r) => r.status === 200 });
}
