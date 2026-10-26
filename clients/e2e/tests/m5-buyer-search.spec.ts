import { expect, test } from "@playwright/test";
import { env, uniqueTag } from "../support/env";
import { approvedSeller, signUpVerifyAndLogin } from "../support/flows";
import { accept, buyNowPaid, sellerOrderIds, ship, waitForDelivery } from "../support/fulfillment";
import { addAddress } from "../support/orders";
import { publish, registerProduct, searchIds } from "../support/products";
import { writeReview } from "../support/reviews";

/**
 * M5 리뷰·검색(REV-1~3, SRC-2): 구매확정한 상품에 내 리뷰에서 사진과 함께 리뷰를 쓰면 검색의 별점순·판매량순·별점 필터와 상품 상세
 * 별점·리뷰에 보인다. 검색창은 입력하는 동안 자동완성을 보인다. 판매자는 새 리뷰 알림에서 그 리뷰로 가 답글을 달고, 답글은 상품 상세에
 * 보인다. 구매확정한 주문은 판매자 정산 예정(STL-2)에 들어간다. 정산 확정은 기간을 줄이는 M5 완료 E2E에서 본다. 관리자가 리뷰를
 * 숨기면 상품 상세에서 빠지고 작성자에게 숨김으로 보이며, 다시 보이면 돌아온다(REV-3, ADM-2). 관리자가 구매 전에 정한 카테고리 수수료율이
 * 정산 예정 내역에 쓰이고, 끝에 기본 요율로 돌린다. 구매확정은 배송 완료 30초 뒤 자동이다(run.sh
 * `FULFILLMENT_AUTO_CONFIRM_AFTER`).
 */
test("M5: 구매확정한 상품에 쓴 리뷰가 검색 정렬·별점 필터·상품 상세에 보이고, 검색창이 자동완성을 보이고, 판매자가 알림에서 답글을 단다", async ({
  browser,
}) => {
  test.setTimeout(360_000);
  const { seller, admin, tag } = await approvedSeller(browser);
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

  await test.step("관리자: 여성의류 수수료율을 8.5%로 정한다(이 뒤 구매확정부터 쓴다)", async () => {
    await admin.goto(`${env.admin}/commission-rates`);
    const row = admin.getByRole("row", { name: /패션의류 > 여성의류/ });
    await row.getByLabel("패션의류 > 여성의류 수수료율(%)").fill("8.5");
    await row.getByRole("button", { name: "저장" }).click();
    await expect(admin.getByText(/수수료율을 정했습니다/)).toBeVisible();
    await expect(admin.getByRole("row", { name: /패션의류 > 여성의류/ })).toContainText("8.5%");
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

  await test.step("판매자: 새 리뷰 알림을 열면 리뷰 목록의 그 리뷰로 가고, 단 답글이 상품 상세에 보인다", async () => {
    await expect(async () => {
      await seller.goto(`${env.seller}/notifications`);
      await expect(seller.getByText(`${reviewed}에 새 리뷰가 등록됐습니다. 별점 5점`)).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    await seller
      .getByRole("listitem", { name: /새 리뷰가 등록됐습니다/ })
      .getByRole("button", { name: /보기$/ })
      .click();
    await expect(seller).toHaveURL(new RegExp(`^${env.seller}/reviews#review-[0-9a-f-]{36}$`));
    // 클라이언트 이동은 :target을 바꾸지 않으므로 주소의 리뷰 ID로 찾는다.
    const target = seller.locator(`#${new URL(seller.url()).hash.slice(1)}`);
    await expect(target).toContainText("두께가 적당하고 구김이 적어요.");
    await target.getByLabel("답글", { exact: true }).fill("찾아 주셔서 감사합니다.");
    await target.getByRole("button", { name: "답글 달기" }).click();
    await expect(seller.getByText("답글을 저장했습니다.")).toBeVisible();
    await expect(seller.getByRole("button", { name: "답글 고치기" })).toBeVisible();

    await buyer.goto(`${env.shop}/products/${reviewedId}`);
    const reviews = buyer.getByRole("region", { name: "리뷰" });
    await expect(reviews.getByText("판매자 답글")).toBeVisible();
    await expect(reviews.getByText("찾아 주셔서 감사합니다.")).toBeVisible();
  });

  await test.step("판매자: 구매확정한 주문이 정산 예정 금액과 내역에 들어간다", async () => {
    await expect(async () => {
      await seller.goto(`${env.seller}/settlements`);
      await expect(seller.getByText("아직 확정되지 않은 구매확정 1건입니다.")).toBeVisible({
        timeout: 1_000,
      });
    }).toPass({ timeout: 30_000 });
    await expect(seller.getByText("64,000원").first()).toBeVisible();
    await seller.getByRole("link", { name: "정산 예정 내역" }).click();
    const items = seller.getByRole("list", { name: "정산 내역" });
    await expect(items.getByText(`${reviewed} × 2`)).toBeVisible();
    // 64,000원 × 8.5% = 5,440원. 구매확정 때의 요율을 고정한다.
    await expect(items.getByText(/수수료 8\.5% 5,440원/)).toBeVisible();
  });

  await test.step("관리자: 리뷰를 숨기면 상품 상세에서 빠지고 작성자에게 숨김으로 보이며, 다시 보이면 돌아온다", async () => {
    await admin.goto(`${env.admin}/reviews?productId=${reviewedId}`);
    const row = admin.getByRole("listitem", { name: `${reviewed} 리뷰` });
    await expect(row).toContainText("두께가 적당하고 구김이 적어요.");
    await row.locator("summary", { hasText: "숨기기" }).click();
    await row.getByLabel("숨기는 사유").fill("E2E 숨김 확인");
    await row.getByRole("button", { name: "숨기기" }).click();
    await expect(admin.getByText(/리뷰를 숨겼습니다/)).toBeVisible();
    await expect(row.getByText("숨김", { exact: true })).toBeVisible();

    await buyer.goto(`${env.shop}/products/${reviewedId}`);
    await expect(
      buyer.getByRole("region", { name: "리뷰" }).getByText("아직 리뷰가 없습니다."),
    ).toBeVisible();
    await buyer.goto(`${env.shop}/my/reviews`);
    await expect(
      buyer.getByRole("list", { name: "내가 쓴 리뷰" }).getByRole("listitem").first(),
    ).toContainText("숨김");

    await admin.goto(`${env.admin}/reviews?status=HIDDEN&productId=${reviewedId}`);
    await row.locator("summary", { hasText: "다시 보이기" }).click();
    await row.getByLabel("다시 보이는 사유").fill("E2E 다시 보임 확인");
    await row.getByRole("button", { name: "다시 보이기" }).click();
    await expect(admin.getByText(/리뷰를 다시 보입니다/)).toBeVisible();

    await buyer.goto(`${env.shop}/products/${reviewedId}`);
    await expect(
      buyer.getByRole("region", { name: "리뷰" }).getByText("두께가 적당하고 구김이 적어요."),
    ).toBeVisible();
  });

  await test.step("관리자: 여성의류 수수료율을 기본으로 돌린다", async () => {
    await admin.goto(`${env.admin}/commission-rates`);
    const row = admin.getByRole("row", { name: /패션의류 > 여성의류/ });
    await row.getByRole("button", { name: "기본으로" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "기본으로" }).click();
    await expect(admin.getByText(/기본 수수료율로 돌렸습니다/)).toBeVisible();
    await expect(admin.getByRole("row", { name: /패션의류 > 여성의류/ })).toContainText("기본");
  });

  await test.step("관리자: 정산 작업을 바로 돌린다(기간이 끝나지 않아 이 구매확정은 아직 정산 예정이다)", async () => {
    await admin.goto(`${env.admin}/settlements`);
    await admin.getByRole("button", { name: "정산 작업 지금 실행" }).click();
    await admin.getByRole("alertdialog").getByRole("button", { name: "지금 실행" }).click();
    await expect(admin.getByText(/정산 작업을 돌렸습니다\. 새로 확정한 정산 \d+건/)).toBeVisible();
  });
});
