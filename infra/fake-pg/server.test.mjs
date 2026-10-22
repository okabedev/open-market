// 가짜 PG 테스트. 레포 루트에서 `node --test infra/fake-pg/`.
import assert from "node:assert/strict";
import { after, before, beforeEach, describe, test } from "node:test";
import { createFakePg } from "./server.mjs";

const ORDER = "0199f000-0000-7000-8000-00000000a001";
const AUTH = { Authorization: `Basic ${Buffer.from("test_sk_fake:").toString("base64")}` };

let server;
let base;

before(async () => {
  server = createFakePg();
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});

after(() => server.close());

beforeEach(async () => {
  await fetch(`${base}/control`, { method: "DELETE" });
});

/** 결제 인증 화면에서 "결제 승인"을 누른 것과 같다. successUrl로 가는 주소를 돌려준다. */
async function authenticate(orderId = ORDER, amount = 39800) {
  const response = await fetch(`${base}/checkout/approve`, {
    method: "POST",
    redirect: "manual",
    body: new URLSearchParams({
      orderId,
      orderName: "반팔 티셔츠 외 1건",
      amount: String(amount),
      successUrl: "http://localhost:3000/orders/x/pay/confirm",
      failUrl: "http://localhost:3000/orders/x/pay",
    }),
  });
  assert.equal(response.status, 303);
  return new URL(response.headers.get("location"));
}

function confirm(body, key = "confirm-1", headers = AUTH) {
  return fetch(`${base}/v1/payments/confirm`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json", "Idempotency-Key": key },
    body: JSON.stringify(body),
  });
}

function cancel(paymentKey, key = "cancel-1", cancelAmount = undefined) {
  return fetch(`${base}/v1/payments/${paymentKey}/cancel`, {
    method: "POST",
    headers: { ...AUTH, "Content-Type": "application/json", "Idempotency-Key": key },
    body: JSON.stringify({ cancelReason: cancelAmount ? "클레임 환불" : "주문 취소", cancelAmount }),
  });
}

async function authenticatedPayment(orderId = ORDER, amount = 39800) {
  const success = await authenticate(orderId, amount);
  return { paymentKey: success.searchParams.get("paymentKey"), orderId, amount };
}

describe("SDK 스크립트", () => {
  test("TossPayments 전역 함수를 정의하는 스크립트를 준다", async () => {
    const response = await fetch(`${base}/v2/standard`);
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type"), /javascript/);
    assert.match(await response.text(), /window\.TossPayments\s*=/);
  });
});

describe("결제 인증 화면", () => {
  test("주문 이름과 금액, 승인·취소 버튼을 보여 준다", async () => {
    const query = new URLSearchParams({
      orderId: ORDER,
      orderName: "반팔 티셔츠 <b>",
      amount: "39800",
      successUrl: "http://localhost:3000/s",
      failUrl: "http://localhost:3000/f",
    });
    const html = await (await fetch(`${base}/checkout?${query}`)).text();
    assert.match(html, /반팔 티셔츠 &lt;b&gt;/);
    assert.match(html, /39,800원/);
    assert.match(html, /결제 승인/);
    assert.match(html, /결제 취소/);
  });

  test("승인하면 결제 키를 만들어 successUrl로 보낸다(토스와 같은 검색 조건)", async () => {
    const success = await authenticate();
    assert.equal(success.pathname, "/orders/x/pay/confirm");
    assert.equal(success.searchParams.get("paymentType"), "NORMAL");
    assert.equal(success.searchParams.get("orderId"), ORDER);
    assert.equal(success.searchParams.get("amount"), "39800");
    assert.match(success.searchParams.get("paymentKey"), /^fake_/);
  });

  test("취소하면 failUrl로 PAY_PROCESS_CANCELED를 보낸다", async () => {
    const response = await fetch(`${base}/checkout/cancel`, {
      method: "POST",
      redirect: "manual",
      body: new URLSearchParams({ orderId: ORDER, failUrl: "http://localhost:3000/f" }),
    });
    assert.equal(response.status, 303);
    const fail = new URL(response.headers.get("location"));
    assert.equal(fail.pathname, "/f");
    assert.equal(fail.searchParams.get("code"), "PAY_PROCESS_CANCELED");
    assert.equal(fail.searchParams.get("orderId"), ORDER);
    assert.ok(fail.searchParams.get("message"));
  });
});

describe("승인 POST /v1/payments/confirm", () => {
  test("인증한 결제를 승인하고 토스 결제 객체를 돌려준다", async () => {
    const payment = await authenticatedPayment();
    const response = await confirm(payment);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.paymentKey, payment.paymentKey);
    assert.equal(body.orderId, ORDER);
    assert.equal(body.status, "DONE");
    assert.equal(body.totalAmount, 39800);
    assert.equal(body.balanceAmount, 39800);
    assert.equal(body.method, "카드");
    assert.match(body.approvedAt, /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+09:00$/);
    assert.ok(body.lastTransactionKey);
  });

  test("시크릿 키가 없으면 401 UNAUTHORIZED_KEY", async () => {
    const payment = await authenticatedPayment();
    const response = await confirm(payment, "confirm-1", {});
    assert.equal(response.status, 401);
    assert.equal((await response.json()).code, "UNAUTHORIZED_KEY");
  });

  test("인증하지 않은 결제 키는 404 NOT_FOUND_PAYMENT_SESSION", async () => {
    const response = await confirm({ paymentKey: "fake_none", orderId: ORDER, amount: 39800 });
    assert.equal(response.status, 404);
    assert.equal((await response.json()).code, "NOT_FOUND_PAYMENT_SESSION");
  });

  test("인증한 금액과 다르면 400 INVALID_REQUEST", async () => {
    const payment = await authenticatedPayment();
    const response = await confirm({ ...payment, amount: 100 });
    assert.equal(response.status, 400);
    assert.equal((await response.json()).code, "INVALID_REQUEST");
  });

  test("같은 멱등 키로 다시 보내면 같은 응답, 다른 키면 ALREADY_PROCESSED_PAYMENT", async () => {
    const payment = await authenticatedPayment();
    const first = await (await confirm(payment)).json();
    const again = await confirm(payment);
    assert.equal(again.status, 200);
    assert.deepEqual(await again.json(), first);
    const other = await confirm(payment, "confirm-2");
    assert.equal(other.status, 400);
    assert.equal((await other.json()).code, "ALREADY_PROCESSED_PAYMENT");
  });

  test("제어 API로 정한 거절을 돌려주고 결제는 ABORTED가 된다", async () => {
    await control(ORDER, {
      confirmError: { status: 403, code: "REJECT_CARD_PAYMENT", message: "한도초과" },
    });
    const payment = await authenticatedPayment();
    const response = await confirm(payment);
    assert.equal(response.status, 403);
    assert.deepEqual(await response.json(), { code: "REJECT_CARD_PAYMENT", message: "한도초과" });
    const found = await (await fetch(`${base}/v1/payments/${payment.paymentKey}`, { headers: AUTH })).json();
    assert.equal(found.status, "ABORTED");
  });

  test("제어 API로 정한 만큼 늦게 응답한다. 승인은 요청을 받은 때 이미 끝나 조회하면 DONE이다", async () => {
    await control(ORDER, { confirmDelayMs: 500 });
    const payment = await authenticatedPayment();
    const started = Date.now();
    const pending = confirm(payment);
    await new Promise((resolve) => setTimeout(resolve, 100));
    const found = await (await fetch(`${base}/v1/payments/${payment.paymentKey}`, { headers: AUTH })).json();
    assert.equal(found.status, "DONE");
    const processing = await confirm(payment);
    assert.equal(processing.status, 409);
    assert.equal((await processing.json()).code, "IDEMPOTENT_REQUEST_PROCESSING");
    assert.equal((await pending).status, 200);
    assert.ok(Date.now() - started >= 500);
  });
});

describe("조회 GET /v1/payments/{paymentKey}", () => {
  test("인증만 한 결제는 READY다", async () => {
    const payment = await authenticatedPayment();
    const response = await fetch(`${base}/v1/payments/${payment.paymentKey}`, { headers: AUTH });
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.status, "READY");
    assert.equal(body.approvedAt, null);
  });

  test("없는 결제는 404 NOT_FOUND_PAYMENT", async () => {
    const response = await fetch(`${base}/v1/payments/fake_none`, { headers: AUTH });
    assert.equal(response.status, 404);
    assert.equal((await response.json()).code, "NOT_FOUND_PAYMENT");
  });
});

describe("취소 POST /v1/payments/{paymentKey}/cancel", () => {
  test("승인한 결제를 전액 취소한다", async () => {
    const payment = await authenticatedPayment();
    await confirm(payment);
    const response = await cancel(payment.paymentKey);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.status, "CANCELED");
    assert.equal(body.balanceAmount, 0);
    assert.equal(body.cancels.length, 1);
    assert.equal(body.cancels[0].cancelAmount, 39800);
    assert.equal(body.cancels[0].cancelReason, "주문 취소");
    assert.ok(body.cancels[0].transactionKey);
  });

  test("같은 멱등 키는 같은 응답, 다른 키면 ALREADY_CANCELED_PAYMENT", async () => {
    const payment = await authenticatedPayment();
    await confirm(payment);
    const first = await (await cancel(payment.paymentKey)).json();
    assert.deepEqual(await (await cancel(payment.paymentKey)).json(), first);
    const other = await cancel(payment.paymentKey, "cancel-2");
    assert.equal(other.status, 400);
    assert.equal((await other.json()).code, "ALREADY_CANCELED_PAYMENT");
  });

  test("취소 금액을 주면 부분취소한다. 남은 금액을 모두 부분취소해도 상태는 PARTIAL_CANCELED다(R9)", async () => {
    const payment = await authenticatedPayment();
    await confirm(payment);
    const first = await cancel(payment.paymentKey, "refund-1", 10000);
    assert.equal(first.status, 200);
    const partial = await first.json();
    assert.equal(partial.status, "PARTIAL_CANCELED");
    assert.equal(partial.balanceAmount, 29800);
    assert.equal(partial.cancels.length, 1);
    assert.equal(partial.cancels[0].cancelAmount, 10000);
    assert.equal(partial.cancels[0].cancelReason, "클레임 환불");

    const rest = await (await cancel(payment.paymentKey, "refund-2", 29800)).json();
    assert.equal(rest.status, "PARTIAL_CANCELED");
    assert.equal(rest.balanceAmount, 0);
    assert.equal(rest.cancels.length, 2);
    assert.equal(rest.lastTransactionKey, rest.cancels[1].transactionKey);
  });

  test("남은 금액보다 큰 부분취소는 403 NOT_CANCELABLE_AMOUNT, 같은 키로 다시 보내면 같은 응답이다", async () => {
    const payment = await authenticatedPayment();
    await confirm(payment);
    const once = await (await cancel(payment.paymentKey, "refund-1", 10000)).json();
    assert.deepEqual(await (await cancel(payment.paymentKey, "refund-1", 10000)).json(), once);
    assert.equal(once.balanceAmount, 29800);

    const over = await cancel(payment.paymentKey, "refund-2", 29801);
    assert.equal(over.status, 403);
    assert.equal((await over.json()).code, "NOT_CANCELABLE_AMOUNT");
  });

  test("제어 API로 정한 취소 거절을 돌려준다. 설정을 지우면 다른 멱등 키로 취소된다", async () => {
    const payment = await authenticatedPayment();
    await confirm(payment);
    await control(ORDER, {
      cancelError: { status: 403, code: "NOT_CANCELABLE_PAYMENT", message: "취소 할 수 없는 결제 입니다." },
    });
    const rejected = await cancel(payment.paymentKey, "refund-1", 10000);
    assert.equal(rejected.status, 403);
    assert.equal((await rejected.json()).code, "NOT_CANCELABLE_PAYMENT");

    await control(ORDER, {});
    const again = await cancel(payment.paymentKey, "refund-1", 10000);
    assert.equal(again.status, 403);
    const retried = await cancel(payment.paymentKey, "refund-2", 10000);
    assert.equal(retried.status, 200);
    assert.equal((await retried.json()).balanceAmount, 29800);
  });

  test("승인하지 않은 결제는 403 NOT_CANCELABLE_PAYMENT", async () => {
    const payment = await authenticatedPayment();
    const response = await cancel(payment.paymentKey);
    assert.equal(response.status, 403);
    assert.equal((await response.json()).code, "NOT_CANCELABLE_PAYMENT");
  });
});

describe("제어 API", () => {
  test("주문의 결제와 승인·취소 요청 수를 보여 준다", async () => {
    const payment = await authenticatedPayment();
    await confirm(payment);
    await confirm(payment);
    await cancel(payment.paymentKey);
    const response = await fetch(`${base}/control/orders/${ORDER}`);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.confirmRequests, 2);
    assert.equal(body.cancelRequests, 1);
    assert.equal(body.payments.length, 1);
    assert.equal(body.payments[0].status, "CANCELED");
  });

  test("DELETE /control은 결제와 설정을 지운다", async () => {
    await control(ORDER, { confirmDelayMs: 10_000 });
    const payment = await authenticatedPayment();
    await fetch(`${base}/control`, { method: "DELETE" });
    const body = await (await fetch(`${base}/control/orders/${ORDER}`)).json();
    assert.deepEqual(body, { confirmRequests: 0, cancelRequests: 0, payments: [] });
    const response = await confirm(payment);
    assert.equal(response.status, 404);
  });

  test("잘못된 설정은 400", async () => {
    const response = await fetch(`${base}/control/orders/${ORDER}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ confirmDelayMs: -1 }),
    });
    assert.equal(response.status, 400);
  });

  test("상태 확인 GET /health", async () => {
    assert.equal((await fetch(`${base}/health`)).status, 200);
  });
});

async function control(orderId, settings) {
  const response = await fetch(`${base}/control/orders/${orderId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings),
  });
  assert.equal(response.status, 204);
}
