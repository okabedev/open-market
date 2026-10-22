import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import {
  accept,
  buyNowPaid,
  openDelivery,
  requestClaim,
  sellerOrderIds,
  ship,
  waitForDelivery,
} from "../support/fulfillment";
import { addAddress, addToCart } from "../support/orders";
import { publish, registerProduct } from "../support/products";

/**
 * M4(docs/roadmap.md) 구매자 화면: 상품 상세에서 바로 구매하고(장바구니를 거치지 않는다), 하위 주문마다 배송 조회에서 송장과 배송
 * 상태를 본다(FUL-2). 발송 전이면 취소되고 환불된다(CLM-1). 배송 완료 뒤 구매확정하거나(FUL-3), 반품·교환을 신청하고 판매자가 거부하면
 * 관리자 중재를 요청한다(CLM-2, CLM-3).
 *
 * run.sh가 자동 구매확정을 배송 완료 30초 뒤로 줄이므로 클레임은 배송 완료를 본 즉시 신청한다.
 */
test("M4: 구매자가 바로 구매하고, 발송 전에 취소하고, 배송 완료 뒤 구매확정하고, 거부된 교환에 중재를 요청한다", async ({
  browser,
}) => {
  test.setTimeout(420_000);
  const { seller, tag } = await approvedSeller(browser);
  const name = `린넨와이드팬츠 ${tag}`;
  const other = `코튼양말 ${tag}`;
  let productId = "";
  let otherId = "";
  await test.step("판매자: 옵션 없는 상품 둘(29,900원 재고 5, 3,000원 재고 5)을 판매한다", async () => {
    productId = await registerProduct(seller, { name, price: 29_900, stock: 5 });
    await publish(seller);
    otherId = await registerProduct(seller, { name: other, price: 3_000, stock: 5 });
    await publish(seller);
  });

  const buyer = await (await browser.newContext()).newPage();
  await test.step("구매자: 가입하고 다른 상품을 장바구니에 담아 둔다", async () => {
    await signUpVerifyAndLogin(buyer, `e2e-buyer-${uniqueTag()}@example.com`, "e2e-buyer-1234");
    await addAddress(buyer);
    await addToCart(buyer, otherId, 1);
  });

  const orders: string[] = [];
  await test.step("바로 구매는 그 상품 하나로 주문서를 열고, 장바구니의 다른 상품은 그대로 남는다", async () => {
    await buyer.goto(`${env.shop}/products/${productId}`);
    await buyer.getByLabel("기본 수량").fill("2");
    await buyer.getByRole("button", { name: "바로 구매" }).click();
    await expect(buyer.getByRole("heading", { name: "주문서" })).toBeVisible();
    await expect(buyer.getByText("59,800원").first()).toBeVisible();
    await expect(buyer.getByText(other)).toHaveCount(0);

    orders.push(await buyNowPaid(buyer, productId, 1));
    orders.push(await buyNowPaid(buyer, productId, 1));
    orders.push(await buyNowPaid(buyer, productId, 1));
    await buyer.goto(`${env.shop}/cart`);
    await expect(buyer.getByRole("link", { name: other })).toBeVisible();
  });
  const [cancelling = "", confirming = "", mediating = ""] = orders;

  await test.step("발송 전이면 배송 조회에서 취소하고, 환불이 끝나면 취소 완료가 된다", async () => {
    await openDelivery(buyer, cancelling);
    await expect(buyer.getByRole("cell", { name: "결제 완료", exact: true })).toBeVisible();
    await buyer.getByRole("button", { name: "주문 취소" }).click();
    await expect(buyer.getByRole("alertdialog")).toContainText("29,900원이 환불됩니다");
    await buyer.getByRole("alertdialog").getByRole("button", { name: "주문 취소" }).click();
    await expect(buyer.getByText("주문을 취소했습니다.")).toBeVisible();
    await waitForDelivery(buyer, cancelling, (page) => page.getByText("취소 완료"));
    await expect(buyer.getByRole("button", { name: "주문 취소" })).toHaveCount(0);
  });

  await test.step("판매자가 나머지 두 하위 주문을 발주 확인·발송하면 구매자 배송 조회에 송장이 보인다", async () => {
    let ids: string[] = [];
    await expect(async () => {
      ids = await sellerOrderIds(seller, name);
      expect(ids).toHaveLength(3);
    }).toPass({ timeout: 30_000 });
    // 최근 결제 순: [mediating, confirming, cancelling]
    const [mediatingSo = "", confirmingSo = ""] = ids;
    await accept(seller, confirmingSo);
    await ship(seller, confirmingSo, "4234567890");
    await accept(seller, mediatingSo);
    await ship(seller, mediatingSo, "5234567890");

    await waitForDelivery(buyer, confirming, (page) =>
      page.getByRole("cell", { name: "CJ대한통운 4234567890" }),
    );
  });

  // 자동 구매확정이 배송 완료 30초 뒤라 두 하위 주문 모두 배송 완료를 본 즉시 처리한다. 먼저 배송 완료된 쪽도 30초 안이다.
  await test.step("교환은 판매자 귀책 사유만 고를 수 있고, 사진을 붙여 신청한다", async () => {
    await waitForDelivery(buyer, mediating, (page) =>
      page.getByRole("link", { name: "반품·교환 신청" }),
    );
    await buyer.getByRole("link", { name: "반품·교환 신청" }).click();
    await buyer.getByRole("radio", { name: /^교환/ }).check();
    await expect(buyer.getByRole("option", { name: "단순 변심" })).toHaveCount(0);
    await requestClaim(buyer, mediating, {
      type: "교환",
      reason: "오배송",
      description: "다른 색상이 왔어요",
      photo: true,
    });
    await expect(buyer.getByText("교환 신청").first()).toBeVisible();
    await expect(buyer.getByText("오배송 (귀책 판매자)")).toBeVisible();
    await expect(buyer.getByRole("img", { name: "클레임 사진 1" })).toBeVisible();
  });

  await test.step("배송 완료가 되면 구매확정한다. 확정한 뒤에는 반품·교환을 신청할 수 없다", async () => {
    await openDelivery(buyer, confirming);
    await expect(buyer.getByText(/자동으로 구매확정됩니다/)).toBeVisible();
    await buyer.getByRole("button", { name: "구매확정" }).click();
    await buyer.getByRole("alertdialog").getByRole("button", { name: "구매확정" }).click();
    await expect(buyer.getByText("구매를 확정했습니다.")).toBeVisible();
    await expect(buyer.getByText(/구매확정했습니다\./)).toBeVisible();
    await expect(buyer.getByRole("link", { name: "반품·교환 신청" })).toHaveCount(0);
  });

  await test.step("판매자가 거부하면 거부 사유를 보고 관리자 중재를 요청한다. 요청은 한 번만 할 수 있다", async () => {
    const [mediatingSo = ""] = await sellerOrderIds(seller, name);
    await seller.goto(`${env.seller}/orders/${mediatingSo}`);
    await seller.getByLabel("거부 사유").fill("주문하신 색상으로 보냈습니다");
    await seller.getByRole("button", { name: "거부", exact: true }).click();
    await expect(seller.getByText("클레임을 거부했습니다.")).toBeVisible();

    await openDelivery(buyer, mediating);
    await expect(buyer.getByText("교환 거부").first()).toBeVisible();
    await expect(buyer.getByText(/주문하신 색상으로 보냈습니다/)).toBeVisible();
    await buyer.getByRole("button", { name: "중재 요청" }).click();
    await buyer.getByRole("alertdialog").getByRole("button", { name: "중재 요청" }).click();
    await expect(buyer.getByText("중재를 요청했습니다.")).toBeVisible();
    await expect(buyer.getByText("교환 중재 중").first()).toBeVisible();
    await expect(buyer.getByRole("button", { name: "중재 요청" })).toHaveCount(0);
    await expect(buyer.getByRole("button", { name: "구매확정" })).toHaveCount(0);
  });
});
