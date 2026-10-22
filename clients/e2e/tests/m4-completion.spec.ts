import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import {
  accept,
  requestClaim,
  sellerOrderIds,
  ship,
  waitForDelivery,
} from "../support/fulfillment";
import {
  addAddress,
  addToCart,
  fakePgOrder,
  openFakePgCheckout,
  orderFromCart,
} from "../support/orders";
import { publish, registerProduct, stockOf } from "../support/products";

/**
 * M4 완료 기준(docs/roadmap.md): 판매자 둘의 상품을 한 번에 주문해 결제하고, 둘 다 발송 → 배송 완료가 된다. 한쪽 하위 주문은 반품 신청
 * → 판매자 승인 → 부분 환불(토스 부분취소, 가짜 PG) → 재입고된다. 다른 하위 주문은 배송 완료 7일 뒤 자동 구매확정된다(FUL-1~3,
 * CLM-2, CLM-3, PAY-2). 7일은 run.sh가 30초(`FULFILLMENT_AUTO_CONFIRM_AFTER`)로 줄인다. 반품은 그 안에 신청한다.
 */
test("M4: 발송 → 배송 완료 → 반품 → 판매자 승인 → 부분 환불 → 재입고, 다른 하위 주문은 자동 구매확정", async ({
  browser,
}) => {
  test.setTimeout(420_000);
  const sellerA = await approvedSeller(browser);
  const sellerB = await approvedSeller(browser);
  const coat = `캐시미어코트 ${sellerA.tag}`;
  const scarf = `울머플러 ${sellerB.tag}`;
  let coatId = "";
  let scarfId = "";
  await test.step("판매자 A·B: 각자 상품을 판매한다(코트 25,000원, 머플러 8,000원, 재고 10)", async () => {
    coatId = await registerProduct(sellerA.seller, { name: coat, price: 25_000, stock: 10 });
    await publish(sellerA.seller);
    scarfId = await registerProduct(sellerB.seller, { name: scarf, price: 8_000, stock: 10 });
    await publish(sellerB.seller);
  });

  const buyer = await (await browser.newContext()).newPage();
  let orderId = "";
  await test.step("구매자: 두 판매자의 상품을 한 번에 주문하고 33,000원을 결제한다", async () => {
    await signUpVerifyAndLogin(buyer, `e2e-buyer-${uniqueTag()}@example.com`, "e2e-buyer-1234");
    await addAddress(buyer);
    await addToCart(buyer, coatId, 1);
    await addToCart(buyer, scarfId, 1);
    orderId = await orderFromCart(buyer);
    await openFakePgCheckout(buyer);
    await expect(buyer.getByText("33,000원").first()).toBeVisible();
    await buyer.getByRole("button", { name: "결제 승인" }).click();
    await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
      timeout: 30_000,
    });
  });

  let coatOrder = "";
  let scarfOrder = "";
  await test.step("판매자마다 자기 하위 주문을 발주 확인하고 발송한다", async () => {
    await expect(async () => {
      [coatOrder = ""] = await sellerOrderIds(sellerA.seller, coat);
      [scarfOrder = ""] = await sellerOrderIds(sellerB.seller, scarf);
      expect(coatOrder).not.toBe("");
      expect(scarfOrder).not.toBe("");
    }).toPass({ timeout: 30_000 });
    await accept(sellerA.seller, coatOrder);
    await ship(sellerA.seller, coatOrder, "8234567890");
    await accept(sellerB.seller, scarfOrder);
    await ship(sellerB.seller, scarfOrder, "9234567890");
  });

  const coatDelivery = { orderId, sellerOrderId: coatOrder };
  const scarfDelivery = { orderId, sellerOrderId: scarfOrder };
  await test.step("배송 시뮬레이터가 배송 완료로 바꾸면 코트를 곧바로 반품 신청한다(단순 변심)", async () => {
    await waitForDelivery(buyer, coatDelivery, (page) =>
      page.getByRole("link", { name: "반품·교환 신청" }),
    );
    await requestClaim(buyer, coatDelivery, {
      type: "반품",
      reason: "단순 변심",
      description: "색상이 생각과 달라요",
    });
  });

  let onHandBefore = 0;
  await test.step("판매자 A가 재입고를 골라 승인하면 반품 배송비를 뺀 19,000원이 부분 환불되고 재입고된다", async () => {
    onHandBefore = (await stockOf(sellerA.seller, coatId)).onHand;
    await sellerA.seller.goto(`${env.seller}/orders/${coatOrder}`);
    await sellerA.seller.getByLabel("재입고한다(다시 팔 수 있다)").check();
    await sellerA.seller.getByRole("button", { name: "승인", exact: true }).click();
    await sellerA.seller.getByRole("alertdialog").getByRole("button", { name: "승인" }).click();
    await expect(sellerA.seller.getByText("클레임을 승인했습니다.")).toBeVisible();

    await waitForDelivery(buyer, coatDelivery, (page) => page.getByText("반품 완료"));
    await expect(buyer.getByText("19,000원")).toBeVisible();
    const pg = await fakePgOrder(orderId);
    expect(pg.payments).toEqual([
      expect.objectContaining({
        status: "PARTIAL_CANCELED",
        totalAmount: 33_000,
        balanceAmount: 14_000,
      }),
    ]);
    await expect
      .poll(async () => (await stockOf(sellerA.seller, coatId)).onHand, { timeout: 30_000 })
      .toBe(onHandBefore + 1);
  });

  await test.step("머플러는 손대지 않아도 배송 완료 7일(30초) 뒤 자동 구매확정된다", async () => {
    await waitForDelivery(
      buyer,
      scarfDelivery,
      (page) => page.getByText(/자동으로 구매확정했습니다/),
      120_000,
    );
    await expect(buyer.getByRole("button", { name: "구매확정" })).toHaveCount(0);
    await expect(buyer.getByRole("link", { name: "반품·교환 신청" })).toHaveCount(0);

    await buyer.goto(`${env.shop}/orders/${orderId}`);
    await expect(buyer.getByText("반품 완료").first()).toBeVisible();
    await expect(buyer.getByText("구매확정").first()).toBeVisible();
  });
});
