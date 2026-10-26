import { expect, type Page } from "@playwright/test";
import { env } from "./env";

export type CouponInput = {
  name: string;
  discountType: "FIXED" | "RATE";
  discountValue: number;
  maxDiscount?: number;
  minOrderAmount: number;
};

const DAY_MS = 24 * 3600_000;

/** 한국 시간 `datetime-local` 값(분 단위). */
function kstInput(date: Date): string {
  return new Date(date.getTime() + 9 * 3600_000).toISOString().slice(0, 16);
}

/**
 * 쿠폰 만들기 폼(판매자·관리자 같은 `CouponForm`)을 채워 지금부터 7일 동안 받을 수 있는 쿠폰(100장, 받은 뒤 7일 사용)을 만든다. 발급
 * 시작 기본값은 다음 정시라 바로 받을 수 있게 1분 전으로 채운다. 만들면 목록에 "발급 중", "0 / 100"으로 보인다.
 */
async function fillCouponForm(page: Page, coupon: CouponInput, submit: string): Promise<void> {
  await page.getByLabel("쿠폰 이름").fill(coupon.name);
  await page
    .getByRole("radio", { name: coupon.discountType === "RATE" ? "정률(%)" : "정액(원)" })
    .check();
  await page
    .getByLabel(coupon.discountType === "RATE" ? "할인율(%)" : "할인 금액(원)")
    .fill(String(coupon.discountValue));
  if (coupon.maxDiscount) {
    await page.getByLabel("최대 할인(원, 선택)").fill(String(coupon.maxDiscount));
  }
  await page.getByLabel("최소 주문 금액(원)").fill(String(coupon.minOrderAmount));
  await page.getByLabel("발급 수량(장)").fill("100");
  const start = new Date(Date.now() - 60_000);
  await page.getByLabel("발급 시작").fill(kstInput(start));
  await page.getByLabel("발급 끝").fill(kstInput(new Date(start.getTime() + 7 * DAY_MS)));
  await page.getByLabel("사용 기한(발급 후 일)").fill("7");
  await page.getByRole("button", { name: submit }).click();
  const row = page.getByRole("row").filter({ hasText: coupon.name });
  await expect(row).toContainText("발급 중");
  await expect(row).toContainText("0 / 100");
}

/** 판매자 센터 쿠폰 만들기 화면에서 판매자 쿠폰을 만든다. */
export async function createSellerCoupon(seller: Page, coupon: CouponInput): Promise<void> {
  await seller.goto(`${env.seller}/coupons`);
  await seller.getByRole("link", { name: "쿠폰 만들기" }).click();
  await expect(seller.getByRole("heading", { name: "쿠폰 만들기" })).toBeVisible();
  await fillCouponForm(seller, coupon, "쿠폰 만들기");
  await expect(seller.getByText("쿠폰을 만들었습니다.")).toBeVisible();
}

/** 관리자 화면 플랫폼 쿠폰 만들기에서 플랫폼 쿠폰을 만든다. 목록에 발행 "플랫폼"으로 보인다. */
export async function createPlatformCoupon(admin: Page, coupon: CouponInput): Promise<void> {
  await admin.goto(`${env.admin}/coupons`);
  await admin.getByRole("link", { name: "플랫폼 쿠폰 만들기" }).click();
  await expect(admin.getByRole("heading", { name: "플랫폼 쿠폰 만들기" })).toBeVisible();
  await fillCouponForm(admin, coupon, "쿠폰 만들기");
  await expect(admin.getByText("플랫폼 쿠폰을 만들었습니다.")).toBeVisible();
  await expect(admin.getByRole("row").filter({ hasText: coupon.name })).toContainText("플랫폼");
}
