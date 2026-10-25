import { expect } from "@playwright/test";
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

/**
 * promotion API로 지금부터 7일 동안 받을 수 있는 쿠폰(100장, 받은 뒤 7일 사용)을 만든다. 판매자 쿠폰은 판매자 토큰, 플랫폼 쿠폰은 관리자
 * 토큰으로 만든다. seller·admin 쿠폰 화면(M5 PR 14·15)이 생기기 전까지 쓴다. 쿠폰 ID를 돌려준다.
 */
export async function createCouponByApi(
  as: { role: "seller"; email: string; password: string } | { role: "admin" },
  coupon: CouponInput,
): Promise<string> {
  const client =
    as.role === "seller"
      ? await OAuthClient.create("seller", env.sellerClientSecret, env.seller)
      : await OAuthClient.create("admin", env.adminClientSecret, env.admin);
  try {
    const { access_token: token } =
      as.role === "seller"
        ? await client.login(as.email, as.password)
        : await client.login(env.adminEmail, env.adminPassword);
    const start = new Date(Date.now() - 60_000);
    const response = await fetch(`${env.gateway}/api/promotion/v1/${as.role}/coupons`, {
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
        issueEndAt: new Date(start.getTime() + 7 * 24 * 3600_000).toISOString(),
        validDays: 7,
      }),
    });
    expect(response.status, await response.clone().text()).toBe(201);
    return ((await response.json()) as { couponId: string }).couponId;
  } finally {
    await client.dispose();
  }
}
