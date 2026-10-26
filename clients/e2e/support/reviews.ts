import { expect, type Page } from "@playwright/test";
import { env } from "./env";
import { PRODUCT_IMAGE } from "./products";

/**
 * 구매자가 shop 내 리뷰에서 구매확정한 상품의 리뷰를 쓴다(REV-1). 구매확정이 이벤트로 review에 닿아 "쓸 수 있는 리뷰"에 보일 때까지
 * 새로 읽으며 기다린다. 사진을 고르면 fixture 한 장을 올리고 미리 보기가 보인 뒤 등록한다.
 */
export async function writeReview(
  buyer: Page,
  productName: string,
  review: { rating: number; content: string; photo?: boolean },
): Promise<void> {
  const write = buyer.getByRole("link", { name: `${productName} 리뷰 쓰기` });
  await expect(async () => {
    await buyer.goto(`${env.shop}/my/reviews`);
    await expect(write).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000, intervals: [1_000] });
  await write.click();
  await expect(buyer.getByRole("heading", { name: "리뷰 쓰기" })).toBeVisible();
  await expect(buyer.getByText(productName)).toBeVisible();
  await buyer.getByRole("radio", { name: `${review.rating}점` }).check();
  await buyer.getByLabel("리뷰", { exact: true }).fill(review.content);
  if (review.photo) {
    await buyer.getByLabel("사진(선택)").setInputFiles(PRODUCT_IMAGE);
    await expect(buyer.getByRole("img", { name: "올린 사진 1" })).toBeVisible();
  }
  await buyer.getByRole("button", { name: "리뷰 등록하기" }).click();
  await expect(buyer).toHaveURL(/\/my\/reviews\?notice=review-written/);
  await expect(buyer.getByText("리뷰를 등록했습니다.")).toBeVisible();
}
