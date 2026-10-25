import { expect } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { env } from "./env";
import { OAuthClient } from "./oauth";

type Writable = { orderLineId: string; productId: string };

/**
 * 구매자 토큰으로 review API를 불러 리뷰를 쓴다. 구매확정이 review에 닿아 작성 자격이 생길 때까지 기다린다. shop 리뷰 작성 화면(M5 PR
 * 13d)이 생기기 전까지 쓴다. 리뷰 ID를 돌려준다.
 */
export async function writeReviewByApi(
  email: string,
  password: string,
  productId: string,
  review: { rating: number; content: string },
): Promise<string> {
  const shop = await OAuthClient.create("shop", env.shopClientSecret, env.shop);
  try {
    const { access_token: token } = await shop.login(email, password);
    const auth = { Authorization: `Bearer ${token}` };
    let orderLineId = "";
    await expect(async () => {
      const response = await fetch(`${env.gateway}/api/review/v1/reviews/writable?size=50`, {
        headers: auth,
      });
      expect(response.status).toBe(200);
      const { items } = (await response.json()) as { items: Writable[] };
      orderLineId = items.find((item) => item.productId === productId)?.orderLineId ?? "";
      expect(orderLineId).not.toBe("");
    }).toPass({ timeout: 30_000, intervals: [1_000] });
    const response = await fetch(`${env.gateway}/api/review/v1/reviews`, {
      method: "POST",
      headers: { ...auth, "Content-Type": "application/json", "Idempotency-Key": randomUUID() },
      body: JSON.stringify({ orderLineId, ...review, fileIds: [] }),
    });
    expect(response.status).toBe(201);
    return ((await response.json()) as { reviewId: string }).reviewId;
  } finally {
    await shop.dispose();
  }
}
