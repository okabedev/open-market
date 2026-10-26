import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import { accept, buyNowPaid, sellerOrderIds, ship, waitForDelivery } from "../support/fulfillment";
import { addAddress } from "../support/orders";
import { publish, registerProduct, searchIds } from "../support/products";
import { writeReview } from "../support/reviews";

/**
 * M5 구매자 리뷰·검색(REV-1, REV-2, SRC-2): 구매확정한 상품에 내 리뷰에서 사진과 함께 리뷰를 쓰면 검색의 별점순·판매량순·별점
 * 필터와 상품 상세 별점·리뷰에 보인다. 검색창은 입력하는 동안 자동완성을 보인다. 구매확정은 배송 완료 30초 뒤 자동이다(run.sh
 * `FULFILLMENT_AUTO_CONFIRM_AFTER`).
 */
test("M5: 구매확정한 상품에 쓴 리뷰가 검색 정렬·별점 필터·상품 상세에 보이고, 검색창이 자동완성을 보인다", async ({
  browser,
}) => {
  test.setTimeout(360_000);
  const { seller, tag } = await approvedSeller(browser);
  const reviewed = `린넨셔츠 ${tag} 오트밀`;
  const plain = `린넨셔츠 ${tag} 네이비`;
  let reviewedId = "";
  let plainId = "";
  await test.step("판매자: 같은 이름 계열 상품 둘을 판매한다", async () => {
    plainId = await registerProduct(seller, { name: plain, price: 29_000, stock: 10 });
    await publish(seller);
    reviewedId = await registerProduct(seller, { name: reviewed, price: 32_000, stock: 10 });
    await publish(seller);
  });

  const email = `e2e-buyer-${uniqueTag()}@example.com`;
  const password = "e2e-buyer-1234";
  const buyer = await (await browser.newContext()).newPage();
  let orderId = "";
  await test.step("구매자: 오트밀 셔츠 2장을 바로 구매하고, 판매자가 발주 확인·발송한다", async () => {
    await signUpVerifyAndLogin(buyer, email, password);
    await addAddress(buyer);
    orderId = await buyNowPaid(buyer, reviewedId, 2);
    let sellerOrderId = "";
    await expect(async () => {
      [sellerOrderId = ""] = await sellerOrderIds(seller, reviewed);
      expect(sellerOrderId).not.toBe("");
    }).toPass({ timeout: 30_000 });
    await accept(seller, sellerOrderId);
    await ship(seller, sellerOrderId, "7234567890");
  });

  await test.step("배송 완료 30초 뒤 자동 구매확정되고, 구매자가 내 리뷰에서 사진과 함께 별점 5점 리뷰를 쓴다", async () => {
    await waitForDelivery(
      buyer,
      orderId,
      (page) => page.getByText(/자동으로 구매확정했습니다/),
      150_000,
    );
    await writeReview(buyer, reviewed, {
      rating: 5,
      content: "두께가 적당하고 구김이 적어요.",
      photo: true,
    });
    const written = buyer.getByRole("list", { name: "내가 쓴 리뷰" }).getByRole("listitem").first();
    await expect(written).toContainText(reviewed);
    await expect(written.getByLabel("별점 5점")).toBeVisible();
    await expect(written.getByRole("img", { name: "리뷰 사진 1" })).toBeVisible();
    await expect(buyer.getByRole("link", { name: `${reviewed} 리뷰 쓰기` })).toHaveCount(0);
  });

  await test.step("검색: 별점순·판매량순 맨 위가 리뷰·판매가 있는 상품이고, 별점 필터는 리뷰 없는 상품을 뺀다", async () => {
    await expect
      .poll(async () => (await searchIds(tag, "RATING"))[0], {
        timeout: 30_000,
        intervals: [1_000],
      })
      .toBe(reviewedId);
    await expect
      .poll(async () => (await searchIds(tag, "SALES"))[0], { timeout: 30_000, intervals: [1_000] })
      .toBe(reviewedId);

    await buyer.goto(`${env.shop}/products?q=${tag}`);
    await buyer
      .getByRole("navigation", { name: "정렬" })
      .getByRole("link", { name: "별점순" })
      .click();
    await expect(buyer).toHaveURL(/sort=RATING/);
    const results = buyer.getByRole("region", { name: "검색 결과" }).getByRole("listitem");
    await expect(results.first()).toContainText(reviewed);
    await expect(results.first()).toContainText("5.0 (리뷰 1개)");

    await buyer
      .getByRole("navigation", { name: "별점" })
      .getByRole("link", { name: "★ 4점 이상" })
      .click();
    await expect(buyer).toHaveURL(/minRating=4/);
    await expect(results).toHaveCount(1);
    await expect(results.first()).toContainText(reviewed);
  });

  await test.step("검색창: 입력하면 자동완성이 판매량 많은 상품명부터 보인다", async () => {
    await buyer.goto(`${env.shop}/products`);
    await buyer.getByRole("combobox", { name: "검색어" }).fill(`린넨셔츠 ${tag}`);
    const options = buyer.locator("#q-suggestions option");
    await expect(options).toHaveCount(2);
    await expect(options.first()).toHaveAttribute("value", reviewed);
    await expect(options.nth(1)).toHaveAttribute("value", plain);
  });

  await test.step("상품 상세: 별점 요약과 리뷰(작성자 없이)가 보인다", async () => {
    await buyer.goto(`${env.shop}/products/${reviewedId}`);
    const reviews = buyer.getByRole("region", { name: "리뷰" });
    await expect(reviews.getByText("5.0 (리뷰 1개)")).toBeVisible();
    await expect(reviews.getByLabel("별점 5점")).toBeVisible();
    await expect(reviews.getByText("두께가 적당하고 구김이 적어요.")).toBeVisible();
    await expect(reviews.getByRole("img", { name: "리뷰 사진 1" })).toBeVisible();
    await buyer.goto(`${env.shop}/products/${plainId}`);
    await expect(
      buyer.getByRole("region", { name: "리뷰" }).getByText("아직 리뷰가 없습니다."),
    ).toBeVisible();
  });
});
