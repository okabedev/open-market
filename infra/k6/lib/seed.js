// 시나리오의 setup()이 쓰는 데이터 준비. 화면 대신 gateway API로 만든다(clients/e2e/support/flows.ts와 같은 순서).
import http from "k6/http";
import { sleep } from "k6";
import { env } from "./env.js";
import { adminToken, login, rolesOf, signUp, uuid, verifyEmails } from "./auth.js";

// init 단계에서만 파일을 열 수 있다. 시나리오 스크립트가 이 모듈을 불러올 때 읽힌다.
const productImage = open("/fixtures/product.jpg", "b");

export function api(method, path, token, body, extra = {}) {
  const headers = { Authorization: `Bearer ${token}`, ...(extra.headers || {}) };
  let payload = body;
  if (body !== undefined && body !== null && !(body.file && body.file.data)) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  const res = http.request(method, `${env.gateway}${path}`, payload === undefined ? null : payload, {
    headers,
    tags: { name: extra.name || "seed" },
  });
  if (extra.expect && !extra.expect.includes(res.status)) {
    throw new Error(`${method} ${path} 실패: ${res.status} ${String(res.body).slice(0, 300)}`);
  }
  return res;
}

/** 국세청 검증 숫자 규칙을 지키는 사업자 등록 번호(member.md "입점 신청"). */
function businessNumber() {
  const d = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  const w = [1, 3, 7, 1, 3, 7, 1, 3, 5];
  const sum = d.reduce((acc, x, i) => acc + x * w[i], 0) + Math.floor((d[8] * 5) / 10);
  d.push((10 - (sum % 10)) % 10);
  const s = d.join("");
  return `${s.slice(0, 3)}-${s.slice(3, 5)}-${s.slice(5)}`;
}

/** 가입 → 이메일 인증 → 입점 신청 → 관리자 승인 → SELLER 역할이 실린 토큰. */
export function approvedSeller(tag, offset) {
  const [seller] = signUp(tag, "seller", 1, offset);
  verifyEmails([seller]);
  let token = login("shop", [seller])[0];
  const application = {
    storeName: `부하시험 상점 ${tag}`,
    businessNumber: businessNumber(),
    representativeName: "부하시험",
    bankName: "국민은행",
    accountHolder: "부하시험",
    accountNumber: `${Date.now()}`.slice(0, 13),
  };
  // member는 이메일 인증을 auth 이벤트로 받는다. 인증 직후에는 아직 모를 수 있어(422 MEMBER_EMAIL_NOT_VERIFIED) 잠깐 기다린다.
  let applied;
  for (let attempt = 0; attempt < 30; attempt++) {
    applied = api("POST", "/api/member/v1/members/me/seller-applications", token, application, { headers: { "Idempotency-Key": uuid() } });
    if (applied.status !== 422 || !String(applied.body).includes("MEMBER_EMAIL_NOT_VERIFIED")) {
      break;
    }
    sleep(1);
  }
  if (applied.status !== 201) {
    throw new Error(`입점 신청 실패: ${applied.status} ${String(applied.body).slice(0, 300)}`);
  }
  const applicationId = applied.json("applicationId");
  api("POST", `/api/member/v1/admin/seller-applications/${applicationId}/approve`, adminToken(), null, { expect: [200, 204] });
  // 승인 이벤트를 auth가 받아 역할을 붙인다. 역할은 토큰을 만들 때 DB에서 읽는다(auth.md "auth 구현").
  for (let attempt = 0; attempt < 30; attempt++) {
    token = login("seller", [seller])[0];
    if (rolesOf(token).includes("SELLER")) {
      return { ...seller, token };
    }
    sleep(1);
  }
  throw new Error("판매자 역할이 붙지 않았다");
}

/** 첫 말단 카테고리. 카테고리는 catalog 마이그레이션이 넣는다. */
function leafCategory() {
  let node = http.get(`${env.gateway}/api/catalog/v1/categories`, { tags: { name: "seed" } }).json()[0];
  while (node.children && node.children.length > 0) {
    node = node.children[0];
  }
  return node.categoryId;
}

/**
 * 판매 중인 상품을 만든다. 상품마다 이미지를 하나 올리고, SKU 하나에 재고를 넉넉히 둔다.
 * @returns {{productId: string, skuId: string, price: number, name: string}[]}
 */
export function products(seller, names, stock) {
  const categoryId = leafCategory();
  return names.map((name, i) => {
    const fileId = api("POST", "/api/file/v1/seller/files?purpose=PRODUCT_IMAGE", seller.token, {
      file: http.file(productImage, "product.jpg", "image/jpeg"),
    }, { expect: [201] }).json("fileId");
    const price = 10000 + i * 100;
    const productId = api("POST", "/api/catalog/v1/seller/products", seller.token, {
      categoryId,
      name,
      description: `${name} 부하 시험용 상품`,
      imageFileIds: [fileId],
      optionNames: [],
      skus: [{ optionValues: [], price, initialStock: stock }],
    }, { headers: { "Idempotency-Key": uuid() }, expect: [201] }).json("productId");
    api("POST", `/api/catalog/v1/seller/products/${productId}/publish`, seller.token, null, { expect: [200, 204] });
    const skuId = api("GET", `/api/catalog/v1/seller/products/${productId}`, seller.token, undefined, { expect: [200] }).json("skus.0.skuId");
    return { productId, skuId, price, name };
  });
}

/** 판매 시작한 상품이 검색에 모두 보일 때까지 기다린다(catalog 이벤트 → search 색인). */
export function waitForSearch(keyword, count) {
  for (let attempt = 0; attempt < 120; attempt++) {
    const res = http.get(`${env.gateway}/api/search/v1/products?q=${encodeURIComponent(keyword)}&size=1`, { tags: { name: "seed" } });
    if (res.status === 200 && res.json("totalCount") >= count) {
      return;
    }
    sleep(1);
  }
  throw new Error(`검색에 '${keyword}' 상품 ${count}개가 보이지 않는다`);
}
