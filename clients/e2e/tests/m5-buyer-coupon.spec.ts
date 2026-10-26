import { expect, test } from "@playwright/test";
import { createPlatformCouponByApi, createSellerCoupon } from "../support/coupons";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import { addAddress, openFakePgCheckout, placeOrder } from "../support/orders";
import { publish, registerProduct } from "../support/products";

/**
 * M5 쿠폰(PRM-1~3): 판매자가 판매자 센터에서 쿠폰을 만들고, 구매자가 플랫폼 쿠폰(쿠폰 받기 화면)과 판매자 쿠폰(상품 상세)을 받아
 * 주문서에서 둘을 골라 할인된 금액으로 결제한다. 결제되면 두 쿠폰은 사용함이 된다. 판매자 목록에 발급 수가 보이고, 발급을 멈추면 상품
 * 상세에서 빠진다. 플랫폼 쿠폰은 admin 쿠폰 화면이 생기기 전까지 API로 만든다(support/coupons.ts).
 */
test("M5: 쿠폰을 받아 주문서에서 판매자·플랫폼 쿠폰을 적용해 할인된 금액으로 결제하고, 쿠폰은 사용함이 된다", async ({
  browser,
}) => {
  test.setTimeout(300_000);
  const { seller, store, tag } = await approvedSeller(browser);
  const product = `캔버스토트백 ${tag}`;
  const sellerCoupon = `토트백 3천원 ${tag}`;
  const platformCoupon = `오픈마켓 10% ${tag}`;
  let productId = "";
  await test.step("판매자 상품 30,000원, 판매자 쿠폰 3,000원(1만 원 이상), 플랫폼 쿠폰 10%(최대 5,000원)", async () => {
    productId = await registerProduct(seller, { name: product, price: 30_000, stock: 10 });
    await publish(seller);
    await createSellerCoupon(seller, {
      name: sellerCoupon,
      discountType: "FIXED",
      discountValue: 3_000,
      minOrderAmount: 10_000,
    });
    await createPlatformCouponByApi({
      name: platformCoupon,
      discountType: "RATE",
      discountValue: 10,
      maxDiscount: 5_000,
      minOrderAmount: 0,
    });
  });

  const buyer = await (await browser.newContext()).newPage();
  await test.step("구매자: 쿠폰 받기 화면에서 플랫폼 쿠폰을, 상품 상세에서 판매자 쿠폰을 받는다", async () => {
    await signUpVerifyAndLogin(buyer, `e2e-buyer-${uniqueTag()}@example.com`, "e2e-buyer-1234");
    await addAddress(buyer);

    await buyer.goto(`${env.shop}/coupons`);
    await buyer.getByRole("button", { name: `${platformCoupon} 받기` }).click();
    await expect(buyer.getByText("쿠폰을 받았습니다.")).toBeVisible();
    await expect(
      buyer.getByRole("listitem").filter({ hasText: platformCoupon }).getByText("받음"),
    ).toBeVisible();

    await buyer.goto(`${env.shop}/products/${productId}`);
    const coupons = buyer.getByRole("region", { name: `${store} 쿠폰` });
    await expect(coupons.getByText("3,000원 할인 · 10,000원 이상 구매 시")).toBeVisible();
    await coupons.getByRole("button", { name: `${sellerCoupon} 받기` }).click();
    await expect(buyer.getByText("쿠폰을 받았습니다.")).toBeVisible();

    await buyer.goto(`${env.shop}/my/coupons`);
    for (const name of [sellerCoupon, platformCoupon]) {
      await expect(buyer.getByRole("listitem").filter({ hasText: name })).toContainText(
        "사용 가능",
      );
    }
  });

  let orderId = "";
  await test.step("주문서: 두 쿠폰을 골라 적용하면 30,000 − 3,000 − 2,700 = 24,300원이고, 그 금액으로 결제한다", async () => {
    await buyer.goto(`${env.shop}/products/${productId}`);
    await buyer.getByLabel("기본 수량").fill("1");
    await buyer.getByRole("button", { name: "바로 구매" }).click();
    await expect(buyer.getByRole("heading", { name: "주문서" })).toBeVisible();
    await buyer
      .getByLabel(`${store} 쿠폰`)
      .selectOption({ label: `${sellerCoupon} · 3,000원 할인 · 10,000원 이상 구매 시` });
    await buyer
      .getByLabel("오픈 마켓 쿠폰")
      .selectOption({ label: `${platformCoupon} · 10% 할인(최대 5,000원)` });
    await buyer.getByRole("button", { name: "쿠폰 적용" }).click();
    await expect(buyer.getByText("-3,000원")).toBeVisible();
    await expect(buyer.getByText("-2,700원")).toBeVisible();
    await expect(buyer.getByRole("button", { name: "24,300원 주문하고 결제하기" })).toBeVisible();

    orderId = await placeOrder(buyer);
    await openFakePgCheckout(buyer);
    await expect(buyer.getByText("24,300원").first()).toBeVisible();
    await buyer.getByRole("button", { name: "결제 승인" }).click();
    await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
      timeout: 30_000,
    });
  });

  await test.step("주문 상세에 할인이 보이고, 두 쿠폰은 사용함이 된다", async () => {
    await buyer.goto(`${env.shop}/orders/${orderId}`);
    await expect(buyer.getByText("판매자 쿠폰 할인")).toBeVisible();
    await expect(buyer.getByText("오픈 마켓 쿠폰 할인")).toBeVisible();
    await expect(buyer.getByText("24,300원").first()).toBeVisible();

    await expect(async () => {
      await buyer.goto(`${env.shop}/my/coupons`);
      for (const name of [sellerCoupon, platformCoupon]) {
        await expect(buyer.getByRole("listitem").filter({ hasText: name })).toContainText(
          "사용함",
          {
            timeout: 1_000,
          },
        );
      }
    }).toPass({ timeout: 30_000, intervals: [1_000] });
  });

  await test.step("판매자: 목록에 발급 수 1장이 보이고, 발급을 멈추면 상품 상세에서 쿠폰이 빠진다", async () => {
    await seller.goto(`${env.seller}/coupons`);
    const row = seller.getByRole("row").filter({ hasText: sellerCoupon });
    await expect(row).toContainText("1 / 100");
    await row.getByRole("button", { name: "발급 중지" }).click();
    await seller.getByRole("alertdialog").getByRole("button", { name: "발급 중지" }).click();
    await expect(seller.getByText("발급을 멈췄습니다.")).toBeVisible();
    await expect(seller.getByRole("row").filter({ hasText: sellerCoupon })).toContainText(
      "발급 중지",
    );
    await expect(row.getByRole("button", { name: "발급 중지" })).toHaveCount(0);

    await buyer.goto(`${env.shop}/products/${productId}`);
    await expect(buyer.getByRole("heading", { name: product })).toBeVisible();
    await expect(buyer.getByRole("region", { name: `${store} 쿠폰` })).toHaveCount(0);
  });
});
