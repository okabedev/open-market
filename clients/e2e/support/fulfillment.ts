import { expect, type Page } from "@playwright/test";
import { randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { env } from "./env";
import { OAuthClient } from "./oauth";
import { addToCart, openFakePgCheckout, orderFromCart } from "./orders";
import { PRODUCT_IMAGE } from "./products";

/** 장바구니에 담아 주문하고 가짜 PG로 결제한다. 결제 완료 화면에서 끝나고 주문 ID를 돌려준다. */
export async function paidOrder(buyer: Page, productId: string, quantity: number): Promise<string> {
  await addToCart(buyer, productId, quantity);
  const orderId = await orderFromCart(buyer);
  await openFakePgCheckout(buyer);
  await buyer.getByRole("button", { name: "결제 승인" }).click();
  await expect(buyer.getByRole("heading", { name: "결제가 완료되었습니다" })).toBeVisible({
    timeout: 30_000,
  });
  return orderId;
}

/** 판매자 하위 주문 목록의 상품 링크가 가리키는 하위 주문 ID. 최근 결제 순이다. */
export async function sellerOrderIds(seller: Page, productName: string): Promise<string[]> {
  await seller.goto(`${env.seller}/orders`);
  const links = seller.getByRole("link", { name: productName });
  await expect(links.first()).toBeVisible();
  const hrefs = await links.evaluateAll((elements) =>
    elements.map((element) => element.getAttribute("href") ?? ""),
  );
  return hrefs.map((href) => href.split("/").at(-1) ?? "");
}

/** 판매자 하위 주문 상세를 다시 읽어 그 요소가 보일 때까지 기다린다(시뮬레이터와 이벤트로 상태가 바뀐다). */
export async function waitForSellerOrder(
  seller: Page,
  sellerOrderId: string,
  visible: (page: Page) => ReturnType<Page["getByText"]>,
  timeout = 60_000,
): Promise<void> {
  await expect(async () => {
    await seller.goto(`${env.seller}/orders/${sellerOrderId}`);
    await expect(visible(seller).first()).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout, intervals: [1_000] });
}

/** 판매자 하위 주문 상세에서 발주를 확인한다. */
export async function accept(seller: Page, sellerOrderId: string): Promise<void> {
  await seller.goto(`${env.seller}/orders/${sellerOrderId}`);
  await seller.getByRole("button", { name: "발주 확인" }).click();
  await seller.getByRole("alertdialog").getByRole("button", { name: "발주 확인" }).click();
  await expect(seller.getByText("발주를 확인했습니다.")).toBeVisible();
}

/** 판매자 하위 주문 상세에서 택배사와 송장 번호를 넣어 발송한다. */
export async function ship(
  seller: Page,
  sellerOrderId: string,
  trackingNumber: string,
): Promise<void> {
  await seller.goto(`${env.seller}/orders/${sellerOrderId}`);
  await seller.getByLabel("택배사").selectOption({ label: "CJ대한통운" });
  await seller.getByLabel("송장 번호").fill(trackingNumber);
  await seller.getByRole("button", { name: "발송", exact: true }).click();
  await expect(seller.getByText("발송했습니다.")).toBeVisible();
}

/**
 * 구매자 API. 구매자 클레임 화면(shop)이 생기기 전에 반품·교환 신청을 gateway로 직접 한다. 토큰은 shop 클라이언트로 받는다.
 */
export class BuyerApi {
  private constructor(
    private readonly oauth: OAuthClient,
    private readonly accessToken: string,
  ) {}

  static async login(email: string, password: string): Promise<BuyerApi> {
    const oauth = await OAuthClient.create("shop", env.shopClientSecret, env.shop);
    return new BuyerApi(oauth, (await oauth.login(email, password)).access_token);
  }

  /** 클레임 사진(`CLAIM_IMAGE`)을 올리고 파일 ID를 돌려준다. */
  async uploadClaimImage(): Promise<string> {
    const form = new FormData();
    form.set(
      "file",
      new Blob([await readFile(PRODUCT_IMAGE)], { type: "image/jpeg" }),
      "claim.jpg",
    );
    const response = await fetch(`${env.gateway}/api/file/v1/files?purpose=CLAIM_IMAGE`, {
      method: "POST",
      headers: { Authorization: `Bearer ${this.accessToken}` },
      body: form,
    });
    expect(response.ok, await response.clone().text()).toBe(true);
    return ((await response.json()) as { fileId: string }).fileId;
  }

  /** 반품·교환을 신청한다(CLM-2). 클레임 ID를 돌려준다. */
  async requestClaim(
    sellerOrderId: string,
    claim: {
      type: "RETURN" | "EXCHANGE";
      reason: string;
      description: string;
      fileIds?: string[];
    },
  ): Promise<string> {
    const response = await fetch(
      `${env.gateway}/api/fulfillment/v1/seller-orders/${sellerOrderId}/claims`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.accessToken}`,
          "Content-Type": "application/json",
          "Idempotency-Key": randomUUID(),
        },
        body: JSON.stringify({ fileIds: [], ...claim }),
      },
    );
    expect(response.status, await response.clone().text()).toBe(201);
    return ((await response.json()) as { claimId: string }).claimId;
  }

  async dispose(): Promise<void> {
    await this.oauth.dispose();
  }
}
