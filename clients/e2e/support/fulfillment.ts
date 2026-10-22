import { expect, type Page } from "@playwright/test";
import { env } from "./env";
import { addToCart, openFakePgCheckout, orderFromCart, placeOrder } from "./orders";
import { PRODUCT_IMAGE } from "./products";

/** 장바구니에 담아 주문하고 가짜 PG로 결제한다. 결제 완료 화면에서 끝나고 주문 ID를 돌려준다. */
export async function paidOrder(buyer: Page, productId: string, quantity: number): Promise<string> {
  await addToCart(buyer, productId, quantity);
  const orderId = await orderFromCart(buyer);
  await openFakePgCheckout(buyer);
  await buyer.getByRole("button", { name: "결제 승인" }).click();
  await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
    timeout: 30_000,
  });
  return orderId;
}

/** 판매자 하위 주문 목록의 상품 링크가 가리키는 하위 주문 ID. 최근 결제 순이다. */
export async function sellerOrderIds(seller: Page, productName: string): Promise<string[]> {
  await seller.goto(`${env.seller}/orders`);
  const links = seller.getByRole("link", { name: productName });
  await expect(links.first()).toBeVisible();
  const hrefs = await links.evaluateAll((elements) =>
    elements.map((element) => element.getAttribute("href") ?? ""),
  );
  return hrefs.map((href) => href.split("/").at(-1) ?? "");
}

/** 판매자 하위 주문 상세를 다시 읽어 그 요소가 보일 때까지 기다린다(시뮬레이터와 이벤트로 상태가 바뀐다). */
export async function waitForSellerOrder(
  seller: Page,
  sellerOrderId: string,
  visible: (page: Page) => ReturnType<Page["getByText"]>,
  timeout = 60_000,
): Promise<void> {
  await expect(async () => {
    await seller.goto(`${env.seller}/orders/${sellerOrderId}`);
    await expect(visible(seller).first()).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout, intervals: [1_000] });
}

/** 판매자 하위 주문 상세에서 발주를 확인한다. */
export async function accept(seller: Page, sellerOrderId: string): Promise<void> {
  await seller.goto(`${env.seller}/orders/${sellerOrderId}`);
  await seller.getByRole("button", { name: "발주 확인" }).click();
  await seller.getByRole("alertdialog").getByRole("button", { name: "발주 확인" }).click();
  await expect(seller.getByText("발주를 확인했습니다.")).toBeVisible();
}

/** 판매자 하위 주문 상세에서 택배사와 송장 번호를 넣어 발송한다. */
export async function ship(
  seller: Page,
  sellerOrderId: string,
  trackingNumber: string,
): Promise<void> {
  await seller.goto(`${env.seller}/orders/${sellerOrderId}`);
  await seller.getByLabel("택배사").selectOption({ label: "CJ대한통운" });
  await seller.getByLabel("송장 번호").fill(trackingNumber);
  await seller.getByRole("button", { name: "발송", exact: true }).click();
  await expect(seller.getByText("발송했습니다.")).toBeVisible();
}

/** 상품 상세에서 옵션 없는 상품을 바로 구매한다(장바구니를 거치지 않는다). 결제 완료 화면에서 끝나고 주문 ID를 돌려준다. */
export async function buyNowPaid(
  buyer: Page,
  productId: string,
  quantity: number,
): Promise<string> {
  await buyer.goto(`${env.shop}/products/${productId}`);
  await buyer.getByLabel("기본 수량").fill(String(quantity));
  await buyer.getByRole("button", { name: "바로 구매" }).click();
  await expect(buyer.getByRole("heading", { name: "주문서" })).toBeVisible();
  await expect(buyer.getByText("주문 상품 1개")).toBeVisible();
  const orderId = await placeOrder(buyer);
  await openFakePgCheckout(buyer);
  await buyer.getByRole("button", { name: "결제 승인" }).click();
  await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
    timeout: 30_000,
  });
  return orderId;
}

/** 구매자 배송 조회의 대상. 주문 ID만 주면 하위 주문이 하나인 주문이다. */
export type Delivery = string | { orderId: string; sellerOrderId: string };

/**
 * 구매자 배송 조회를 연다. 주문 ID만 주면 주문 상세에서 하나뿐인 "배송 조회" 링크를 누르고, 하위 주문 ID까지 주면 그 화면으로 바로
 * 간다(판매자가 둘인 주문).
 */
export async function openDelivery(buyer: Page, delivery: Delivery): Promise<void> {
  if (typeof delivery === "string") {
    await buyer.goto(`${env.shop}/orders/${delivery}`);
    await buyer.getByRole("link", { name: /배송 조회/ }).click();
  } else {
    await buyer.goto(
      `${env.shop}/orders/${delivery.orderId}/seller-orders/${delivery.sellerOrderId}`,
    );
  }
  await expect(buyer.getByRole("heading", { name: /배송 조회/ })).toBeVisible();
}

/** 구매자 배송 조회를 다시 읽어 그 요소가 보일 때까지 기다린다. */
export async function waitForDelivery(
  buyer: Page,
  orderId: Delivery,
  visible: (page: Page) => ReturnType<Page["getByText"]>,
  timeout = 60_000,
): Promise<void> {
  await expect(async () => {
    await openDelivery(buyer, orderId);
    await expect(visible(buyer).first()).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout, intervals: [1_000] });
}

/**
 * 구매자 배송 조회에서 반품·교환을 신청한다(shop 화면). 사진은 `photo`면 fixture 이미지 한 장을 올린다. 배송 조회로 돌아와 끝난다.
 */
export async function requestClaim(
  buyer: Page,
  orderId: Delivery,
  claim: { type: "반품" | "교환"; reason: string; description: string; photo?: boolean },
): Promise<void> {
  await openDelivery(buyer, orderId);
  await buyer.getByRole("link", { name: "반품·교환 신청" }).click();
  await expect(buyer.getByRole("heading", { name: "반품·교환 신청" })).toBeVisible();
  await buyer.getByRole("radio", { name: new RegExp(`^${claim.type}`) }).check();
  await buyer.getByLabel("사유").selectOption({ label: claim.reason });
  await buyer.getByLabel("자세한 내용").fill(claim.description);
  if (claim.photo) {
    await buyer.getByLabel("사진(선택)").setInputFiles(PRODUCT_IMAGE);
    await expect(buyer.getByRole("img", { name: "올린 사진 1" })).toBeVisible();
  }
  await buyer.getByRole("button", { name: "신청하기" }).click();
  await expect(buyer.getByText("신청했습니다.")).toBeVisible();
}
