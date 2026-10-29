import { expect, test } from "@playwright/test";
import { createSellerCoupon } from "../support/coupons";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import { accept, sellerOrderIds, ship, waitForDelivery } from "../support/fulfillment";
import { addAddress, openFakePgCheckout, placeOrder } from "../support/orders";
import { publish, registerProduct } from "../support/products";
import { writeReview } from "../support/reviews";

/**
 * M5 완료 기준(docs/roadmap.md): 선착순 쿠폰을 받아 주문에 적용하고, 구매확정 후 리뷰를 쓰면 검색 결과의 별점이 바뀐다. 정산 배치를 두
 * 번 돌려도 정산 금액이 같다. 이어서 관리자가 정산 계좌를 보고 지급하면 판매자에게 지급 완료와 알림이 보인다.
 *
 * 금액: 상품 40,000원 − 판매자 쿠폰 4,000원 = 결제 36,000원. 정산은 판매 금액 40,000원 − 수수료 10%(기본 요율) 4,000원 − 판매자 쿠폰
 * 부담 4,000원 = 32,000원. 정산 기간은 1분, 마감 지연은 5초다(run.sh `SETTLEMENT_PERIOD_LENGTH`, `SETTLEMENT_CLOSE_DELAY`). 정기 정산
 * 작업은 매주 월요일이라 E2E 중에는 돌지 않고, 관리자 즉시 실행으로만 확정한다.
 */
test("M5: 선착순 쿠폰 주문 → 구매확정 → 리뷰가 검색 별점에 반영되고, 정산을 두 번 돌려도 금액이 같으며 지급까지 된다", async ({
  browser,
}) => {
  test.setTimeout(420_000);
  const { seller, admin, store, tag } = await approvedSeller(browser);
  const product = `울블렌드코트 ${tag}`;
  const coupon = `코트 4천원 ${tag}`;
  let productId = "";
  await test.step("판매자: 상품 40,000원을 판매하고 선착순 쿠폰 4,000원(1만 원 이상)을 만든다", async () => {
    productId = await registerProduct(seller, { name: product, price: 40_000, stock: 10 });
    await publish(seller);
    await createSellerCoupon(seller, {
      name: coupon,
      discountType: "FIXED",
      discountValue: 4_000,
      minOrderAmount: 10_000,
    });
  });

  const buyer = await (await browser.newContext()).newPage();
  let orderId = "";
  await test.step("구매자: 상품 상세에서 쿠폰을 받아 주문서에 적용하고 36,000원을 결제한다", async () => {
    await signUpVerifyAndLogin(buyer, `e2e-buyer-${uniqueTag()}@example.com`, "e2e-buyer-1234");
    await addAddress(buyer);
    await buyer.goto(`${env.shop}/products/${productId}`);
    await buyer
      .getByRole("region", { name: `${store} 쿠폰` })
      .getByRole("button", { name: `${coupon} 받기` })
      .click();
    await expect(buyer.getByText("쿠폰을 받았습니다.")).toBeVisible();

    await buyer.getByLabel("기본 수량").fill("1");
    await buyer.getByRole("button", { name: "바로 구매" }).click();
    await expect(buyer.getByRole("heading", { name: "주문서" })).toBeVisible();
    await buyer
      .getByLabel(`${store} 쿠폰`)
      .selectOption({ label: `${coupon} · 4,000원 할인 · 10,000원 이상 구매 시` });
    await buyer.getByRole("button", { name: "쿠폰 적용" }).click();
    await expect(buyer.getByText("-4,000원")).toBeVisible();
    await expect(buyer.getByRole("button", { name: "36,000원 주문하고 결제하기" })).toBeVisible();
    orderId = await placeOrder(buyer);
    await openFakePgCheckout(buyer);
    await expect(buyer.getByText("36,000원").first()).toBeVisible();
    await buyer.getByRole("button", { name: "결제 승인" }).click();
    await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
      timeout: 30_000,
    });
  });

  await test.step("판매자가 발송하고, 배송 완료 30초 뒤 자동 구매확정된다", async () => {
    let sellerOrderId = "";
    await expect(async () => {
      [sellerOrderId = ""] = await sellerOrderIds(seller, product);
      expect(sellerOrderId).not.toBe("");
    }).toPass({ timeout: 30_000 });
    await accept(seller, sellerOrderId);
    await ship(seller, sellerOrderId, "8234567890");
    await waitForDelivery(
      buyer,
      orderId,
      (page) => page.getByText(/자동으로 구매확정했습니다/),
      150_000,
    );
  });

  await test.step("구매자가 별점 4점 리뷰를 쓰면 검색 결과의 별점이 바뀐다", async () => {
    await buyer.goto(`${env.shop}/products?q=${tag}`);
    const results = buyer.getByRole("region", { name: "검색 결과" }).getByRole("listitem");
    await expect(results.first()).toContainText(product);
    // 리뷰가 없으면 카드에 별점 줄이 없다.
    await expect(results.first()).not.toContainText("리뷰 1개");

    await writeReview(buyer, product, { rating: 4, content: "따뜻하고 핏이 예뻐요. 추천합니다." });
    await expect(async () => {
      await buyer.goto(`${env.shop}/products?q=${tag}`);
      await expect(results.first()).toContainText("4.0 (리뷰 1개)", { timeout: 1_000 });
    }).toPass({ timeout: 30_000, intervals: [1_000] });
  });

  await test.step("판매자: 구매확정한 주문이 정산 예정 32,000원으로 보인다", async () => {
    await expect(async () => {
      await seller.goto(`${env.seller}/settlements`);
      await expect(seller.getByText("아직 확정되지 않은 구매확정 1건입니다.")).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    const pending = seller.getByRole("definition").filter({ hasText: "32,000원" });
    await expect(pending.first()).toBeVisible();
  });

  let settlementPath = "";
  await test.step("관리자: 정산 기간이 끝난 뒤 정산 작업을 돌리면 판매자 정산이 32,000원으로 확정된다", async () => {
    const rows = seller.getByRole("region", { name: "확정된 정산" }).getByRole("row");
    await expect(async () => {
      await admin.goto(`${env.admin}/settlements`);
      await admin.getByRole("button", { name: "정산 작업 지금 실행" }).click();
      await admin.getByRole("alertdialog").getByRole("button", { name: "지금 실행" }).click();
      await expect(admin.getByText(/정산 작업을 돌렸습니다/)).toBeVisible();
      await seller.goto(`${env.seller}/settlements`);
      // 머리글 행과 정산 하나
      await expect(rows).toHaveCount(2, { timeout: 1_000 });
    }).toPass({ timeout: 150_000, intervals: [10_000] });
    await expect(rows.nth(1)).toContainText("32,000원");
    await expect(rows.nth(1)).toContainText("지급 대기");
    await expect(seller.getByText("아직 확정되지 않은 구매확정 0건입니다.")).toBeVisible();
    settlementPath = new URL(
      (await rows.nth(1).getByRole("link").first().getAttribute("href")) ?? "",
      env.seller,
    ).pathname;
    expect(settlementPath).toMatch(/^\/settlements\/[0-9a-f-]{36}$/);
  });

  await test.step("관리자가 정산 작업을 한 번 더 돌려도 정산은 하나이고 금액이 같다(STL-4)", async () => {
    await admin.goto(`${env.admin}/settlements`);
    await admin.getByRole("button", { name: "정산 작업 지금 실행" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "지금 실행" }).click();
    await expect(admin.getByText(/정산 작업을 돌렸습니다/)).toBeVisible();
    // settlement는 상호가 없다. 관리자 목록은 member에 물어 상호를 보인다.
    await expect(admin.getByRole("row").filter({ hasText: store })).toHaveCount(1);
    await expect(admin.getByRole("row").filter({ hasText: store })).toContainText("32,000원");

    await seller.goto(`${env.seller}/settlements`);
    const rows = seller.getByRole("region", { name: "확정된 정산" }).getByRole("row");
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(1)).toContainText("32,000원");

    await seller.goto(`${env.seller}${settlementPath}`);
    await expect(seller.getByText("40,000원").first()).toBeVisible();
    await expect(seller.getByText("-4,000원", { exact: true })).toHaveCount(2);
    await expect(seller.getByText("32,000원").first()).toBeVisible();
    await expect(
      seller.getByRole("list", { name: "정산 내역" }).getByText(`${product} × 1`),
    ).toBeVisible();
  });

  await test.step("관리자: 정산 상세에서 상호와 정산 계좌를 보고 지급하면, 판매자에게 지급 완료와 알림이 보인다", async () => {
    await admin.goto(`${env.admin}${settlementPath}`);
    await expect(
      admin.getByRole("heading", { name: new RegExp(`${store} .* 정산`) }),
    ).toBeVisible();
    await expect(admin.getByText("정산 계좌", { exact: true })).toBeVisible();
    await expect(admin.getByText(/\*+\d{4}/)).toBeVisible();
    await admin.getByRole("button", { name: "지급 완료로 기록" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "지급 완료" }).click();
    await expect(admin.getByText("지급 완료로 기록했습니다.", { exact: false })).toBeVisible();
    await expect(admin.getByRole("button", { name: "지급 완료로 기록" })).toHaveCount(0);

    await seller.goto(`${env.seller}${settlementPath}`);
    await expect(seller.getByText("지급 완료", { exact: true })).toBeVisible();
    await expect(async () => {
      await seller.goto(`${env.seller}/notifications`);
      await expect(seller.getByText("정산 금액 32,000원이 지급됐습니다.")).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    await expect(seller.getByText(/판매분 정산이 확정됐습니다\. 정산 금액 32,000원/)).toBeVisible();
    await seller
      .getByRole("listitem", { name: /지급됐습니다/ })
      .getByRole("button", { name: /보기$/ })
      .click();
    await expect(seller).toHaveURL(`${env.seller}${settlementPath}`);
  });
});
