import { expect, type Page } from "@playwright/test";
import { env } from "./env";

/** shop 배송지 화면에서 배송지를 하나 더한다. 첫 배송지는 기본 배송지가 된다. */
export async function addAddress(buyer: Page): Promise<void> {
  await buyer.goto(`${env.shop}/my/addresses/new`);
  await buyer.getByLabel("이름표").fill("집");
  await buyer.getByLabel("받는 사람", { exact: true }).fill("홍길동");
  await buyer.getByLabel("받는 사람 연락처").fill("010-1234-5678");
  await buyer.getByLabel("우편번호").fill("06236");
  await buyer.getByLabel("기본 주소").fill("서울특별시 강남구 테헤란로 152");
  await buyer.getByLabel("상세 주소").fill("12층");
  await buyer.getByRole("button", { name: "추가" }).click();
  await buyer.waitForURL(`${env.shop}/my/addresses`);
}

/** 상품 상세에서 옵션 없는 상품(SKU 하나)을 수량만큼 장바구니에 담는다. */
export async function addToCart(buyer: Page, productId: string, quantity: number): Promise<void> {
  await buyer.goto(`${env.shop}/products/${productId}`);
  await buyer.getByLabel("기본 수량").fill(String(quantity));
  await buyer.getByRole("button", { name: "담기" }).click();
  await expect(buyer.getByText(/장바구니에 담았습니다/)).toBeVisible();
}

/** 장바구니에서 선택한 상품(처음에는 모두)으로 주문서를 연다. 기본 배송지가 골라져 있다. */
export async function openCheckout(buyer: Page): Promise<void> {
  await buyer.goto(`${env.shop}/cart`);
  await buyer.getByRole("button", { name: "선택한 상품 주문하기" }).click();
  await expect(buyer.getByRole("heading", { name: "주문서" })).toBeVisible();
  await expect(buyer.getByRole("radio")).toBeChecked();
}

/** 열린 주문서에서 주문한다. 결제 화면에서 끝나고 주문 ID를 돌려준다. */
export async function placeOrder(buyer: Page): Promise<string> {
  await buyer.getByRole("button", { name: /주문하고 결제하기/ }).click();
  await buyer.waitForURL(/\/orders\/[0-9a-f-]+\/pay$/);
  const orderId = new URL(buyer.url()).pathname.split("/")[2];
  if (!orderId) {
    throw new Error(`결제 화면이 아니다: ${buyer.url()}`);
  }
  return orderId;
}

/** 장바구니에서 선택한 상품으로 기본 배송지 주문을 한다. 결제 화면에서 끝나고 주문 ID를 돌려준다. */
export async function orderFromCart(buyer: Page): Promise<string> {
  await openCheckout(buyer);
  return placeOrder(buyer);
}

/** 결제 화면에서 결제하기를 눌러 가짜 PG 결제 인증 화면으로 간다. 재고 확인(PENDING)이 끝나야 버튼이 켜진다. */
export async function openFakePgCheckout(buyer: Page): Promise<void> {
  const pay = buyer.getByRole("button", { name: "결제하기" });
  await expect(pay).toBeEnabled({ timeout: 30_000 });
  await pay.click();
  await expect(buyer.getByRole("heading", { name: "가짜 PG 결제 인증" })).toBeVisible();
}

export type FakePgOrder = {
  confirmRequests: number;
  cancelRequests: number;
  payments: { paymentKey: string; status: string; totalAmount: number }[];
};

/** 가짜 PG 제어 API로 본 주문의 결제와 승인·취소 요청 수. */
export async function fakePgOrder(orderId: string): Promise<FakePgOrder> {
  const response = await fetch(`${env.fakePg}/control/orders/${orderId}`);
  expect(response.status).toBe(200);
  return (await response.json()) as FakePgOrder;
}

/** 가짜 PG 제어 API로 그 주문의 승인 응답 지연(ms)이나 거절을 정한다. 결제 인증 전에 부른다. */
export async function configureFakePg(
  orderId: string,
  settings: {
    confirmDelayMs?: number;
    confirmError?: { status: number; code: string; message: string };
  },
): Promise<void> {
  const response = await fetch(`${env.fakePg}/control/orders/${orderId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(settings),
  });
  expect(response.status).toBe(204);
}

/** 장바구니에서 그 상품의 줄을 뺀다. */
export async function removeFromCart(buyer: Page, productName: string): Promise<void> {
  await buyer.goto(`${env.shop}/cart`);
  const line = buyer
    .getByRole("listitem")
    .filter({ has: buyer.getByRole("link", { name: productName }) });
  await line.getByRole("button", { name: "빼기" }).click();
  await expect(buyer.getByRole("link", { name: productName })).toHaveCount(0);
}

/** 구매자 주문 상세를 다시 읽어 그 문구가 보일 때까지 기다린다(이벤트로 상태가 바뀐다). */
export async function waitForOrderText(
  buyer: Page,
  orderId: string,
  text: string | RegExp,
  timeout = 30_000,
): Promise<void> {
  await expect(async () => {
    await buyer.goto(`${env.shop}/orders/${orderId}`);
    await expect(buyer.getByText(text).first()).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout, intervals: [1_000] });
}
