import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import {
  addAddress,
  addToCart,
  fakePgOrder,
  openFakePgCheckout,
  orderFromCart,
} from "../support/orders";
import { publish, registerProduct } from "../support/products";

/**
 * M3(docs/roadmap.md): 구매자가 장바구니에서 주문하고 결제위젯으로 결제하면 결제 완료가 되고, 구매자 주문 내역과 판매자 하위 주문에
 * 보인다. 결제 전 주문은 구매자가 취소한다(ORD-1~4, PAY-1). 결제는 토스 대신 가짜 PG(infra/fake-pg, ADR 0018)가 받는다. shop은 SDK
 * 스크립트 주소만, payment는 토스 API 주소만 바뀐다.
 */
test("M3: 장바구니에서 주문해 결제하면 주문 내역과 판매자 주문에 보이고, 결제 전 주문은 취소할 수 있다", async ({
  browser,
}) => {
  test.setTimeout(300_000);
  const { seller, tag } = await approvedSeller(browser);
  const name = `순면반팔티셔츠 ${tag}`;
  let productId = "";
  await test.step("판매자: 옵션 없는 상품(19,900원, 재고 5)을 등록하고 판매를 시작한다", async () => {
    productId = await registerProduct(seller, { name, price: 19_900, stock: 5 });
    await publish(seller);
  });

  const buyer = await (await browser.newContext()).newPage();
  const email = `e2e-buyer-${uniqueTag()}@example.com`;
  let orderId = "";
  await test.step("구매자: 가입하고 배송지를 더한 뒤 2개를 담아 주문한다", async () => {
    await signUpVerifyAndLogin(buyer, email, "e2e-buyer-1234");
    await addAddress(buyer);
    await addToCart(buyer, productId, 2);
    orderId = await orderFromCart(buyer);
  });

  await test.step("결제 인증 화면에서 취소하면 결제 화면으로 돌아와 다시 결제할 수 있다", async () => {
    await openFakePgCheckout(buyer);
    await expect(buyer.getByText("39,800원")).toBeVisible();
    await buyer.getByRole("button", { name: "결제 취소" }).click();
    await buyer.waitForURL(new RegExp(`/orders/${orderId}/pay\\?`));
    await expect(buyer.getByText("결제를 취소했습니다. 다시 결제할 수 있습니다.")).toBeVisible();
  });

  await test.step("다시 결제하고 인증을 마치면 BFF가 승인하고 결제 완료 화면이 보인다", async () => {
    await openFakePgCheckout(buyer);
    await buyer.getByRole("button", { name: "결제 승인" }).click();
    await buyer.waitForURL(`${env.shop}/orders/${orderId}/complete`);
    await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
      timeout: 30_000,
    });
    await expect(buyer.getByText(`${name} × 2`)).toBeVisible();
    await expect(buyer.getByText("39,800원").first()).toBeVisible();

    const pg = await fakePgOrder(orderId);
    expect(pg.confirmRequests).toBe(1);
    expect(pg.payments).toEqual([expect.objectContaining({ status: "DONE", totalAmount: 39_800 })]);
  });

  await test.step("결제된 상품은 장바구니에서 빠진다", async () => {
    await expect(async () => {
      await buyer.goto(`${env.shop}/cart`);
      await expect(buyer.getByText("장바구니가 비어 있습니다.")).toBeVisible({ timeout: 1_000 });
    }).toPass({ timeout: 30_000 });
  });

  await test.step("구매자 주문 내역과 상세에 결제 완료로 보인다(ORD-3)", async () => {
    await buyer.goto(`${env.shop}/orders`);
    const row = buyer.getByRole("row", { name: new RegExp(name) });
    await expect(row).toContainText("결제 완료");
    await expect(row).toContainText("39,800원");
    await row.getByRole("link", { name }).click();
    await buyer.waitForURL(`${env.shop}/orders/${orderId}`);
    await expect(buyer.getByRole("heading", { name })).toBeVisible();
    await expect(buyer.getByText("결제 완료")).toBeVisible();
    await expect(buyer.getByText("홍길동 · 010-1234-5678")).toBeVisible();
    await expect(buyer.getByRole("button", { name: "주문 취소" })).toHaveCount(0);
  });

  await test.step("판매자 주문 목록과 상세에 하위 주문이 배송지와 함께 보인다(ORD-4)", async () => {
    await seller.goto(`${env.seller}/orders`);
    const row = seller.getByRole("row", { name: new RegExp(name) });
    await expect(row).toContainText("결제 완료");
    await expect(row).toContainText("39,800원");
    await row.getByRole("link", { name }).click();
    await expect(seller.getByRole("heading", { name: `주문 ${orderId}` })).toBeVisible();
    await expect(seller.getByRole("row", { name: new RegExp(name) })).toContainText("19,900원");
    await expect(seller.getByText("서울특별시 강남구 테헤란로 152 12층")).toBeVisible();
  });

  await test.step("결제 전 주문은 주문 상세에서 취소하고, 판매자에게는 보이지 않는다", async () => {
    await addToCart(buyer, productId, 1);
    const cancelledId = await orderFromCart(buyer);
    await buyer.goto(`${env.shop}/orders/${cancelledId}`);
    await buyer.getByRole("button", { name: "주문 취소" }).click();
    await buyer.getByRole("alertdialog").getByRole("button", { name: "주문 취소" }).click();
    await expect(buyer.getByText("결제 전에 취소한 주문입니다.")).toBeVisible();
    await expect(buyer.getByText("취소됨")).toBeVisible();
    expect((await fakePgOrder(cancelledId)).confirmRequests).toBe(0);

    await seller.goto(`${env.seller}/orders`);
    await expect(seller.getByText("전체 1건")).toBeVisible();
  });
});
