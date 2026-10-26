import { expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { env } from "./env";
import { OAuthClient } from "./oauth";

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
 * 판매자 센터 쿠폰 만들기 화면에서 지금부터 7일 동안 받을 수 있는 쿠폰(100장, 받은 뒤 7일 사용)을 만든다. 발급 시작 기본값은 다음
 * 정시라 바로 받을 수 있게 1분 전으로 채운다. 만들면 목록에 "발급 중"으로 보인다.
 */
export async function createSellerCoupon(seller: Page, coupon: CouponInput): Promise<void> {
  await seller.goto(`${env.seller}/coupons`);
  await seller.getByRole("link", { name: "쿠폰 만들기" }).click();
  await expect(seller.getByRole("heading", { name: "쿠폰 만들기" })).toBeVisible();
  await seller.getByLabel("쿠폰 이름").fill(coupon.name);
  await seller
    .getByRole("radio", { name: coupon.discountType === "RATE" ? "정률(%)" : "정액(원)" })
    .check();
  await seller
    .getByLabel(coupon.discountType === "RATE" ? "할인율(%)" : "할인 금액(원)")
    .fill(String(coupon.discountValue));
  if (coupon.maxDiscount) {
    await seller.getByLabel("최대 할인(원, 선택)").fill(String(coupon.maxDiscount));
  }
  await seller.getByLabel("최소 주문 금액(원)").fill(String(coupon.minOrderAmount));
  await seller.getByLabel("발급 수량(장)").fill("100");
  const start = new Date(Date.now() - 60_000);
  await seller.getByLabel("발급 시작").fill(kstInput(start));
  await seller.getByLabel("발급 끝").fill(kstInput(new Date(start.getTime() + 7 * DAY_MS)));
  await seller.getByLabel("사용 기한(발급 후 일)").fill("7");
  await seller.getByRole("button", { name: "쿠폰 만들기" }).click();
  await expect(seller.getByText("쿠폰을 만들었습니다.")).toBeVisible();
  const row = seller.getByRole("row").filter({ hasText: coupon.name });
  await expect(row).toContainText("발급 중");
  await expect(row).toContainText("0 / 100");
}

/**
 * 관리자 토큰으로 promotion API를 불러 지금부터 7일 동안 받을 수 있는 플랫폼 쿠폰(100장, 받은 뒤 7일 사용)을 만든다. admin 쿠폰
 * 화면(M5 PR 15)이 생기기 전까지 쓴다. 쿠폰 ID를 돌려준다.
 */
export async function createPlatformCouponByApi(coupon: CouponInput): Promise<string> {
  const client = await OAuthClient.create("admin", env.adminClientSecret, env.admin);
  try {
    const { access_token: token } = await client.login(env.adminEmail, env.adminPassword);
    const start = new Date(Date.now() - 60_000);
    const response = await fetch(`${env.gateway}/api/promotion/v1/admin/coupons`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "Idempotency-Key": randomUUID(),
      },
      body: JSON.stringify({
        ...coupon,
        quantity: 100,
        issueStartAt: start.toISOString(),
        issueEndAt: new Date(start.getTime() + 7 * DAY_MS).toISOString(),
        validDays: 7,
      }),
    });
    expect(response.status, await response.clone().text()).toBe(201);
    return ((await response.json()) as { couponId: string }).couponId;
  } finally {
    await client.dispose();
  }
}
