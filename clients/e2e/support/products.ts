import { expect, type Page } from "@playwright/test";
import path from "node:path";
import { env } from "./env";

/** 상품 이미지 픽스처(1200×800 JPEG). */
export const PRODUCT_IMAGE = path.resolve(import.meta.dirname, "../fixtures/product.jpg");

/** 시드 카테고리 패션의류 > 여성의류(catalog V1 마이그레이션의 고정 ID). */
export const WOMEN_CLOTHING = "0199f000-0000-7000-8000-000000000101";

export type ProductInput = {
  name: string;
  /** 옵션 이름 하나와 그 값들. 없으면 옵션 없는 상품(SKU 하나) */
  option?: { name: string; values: string[] };
  price: number;
  stock: number;
};

/**
 * 판매자 센터에서 이미지 한 장과 옵션 조합으로 상품을 등록한다(CAT-1). 등록 중(DRAFT) 상세 화면에서 끝나고 상품 ID를 돌려준다.
 */
export async function registerProduct(seller: Page, input: ProductInput): Promise<string> {
  await seller.goto(`${env.seller}/products/new`);
  await seller.getByLabel("카테고리").selectOption(WOMEN_CLOTHING);
  await seller.getByLabel("상품 이름").fill(input.name);
  await seller.getByLabel("상품 설명").fill(`${input.name} 상품입니다. 면 100%.`);
  await seller.getByLabel("이미지 올리기").setInputFiles(PRODUCT_IMAGE);
  await expect(seller.getByRole("img", { name: "이미지 1" })).toBeVisible();
  if (input.option) {
    await seller.getByRole("button", { name: "옵션 추가" }).click();
    await seller.getByLabel("옵션 1 이름").fill(input.option.name);
    await seller.getByLabel("값(쉼표로 구분)").fill(input.option.values.join(", "));
    await seller.getByRole("button", { name: "조합 만들기" }).click();
    await seller.getByLabel("가격 일괄 입력").fill(String(input.price));
    await seller.getByLabel("재고 일괄 입력").fill(String(input.stock));
    await seller.getByRole("button", { name: "모두 적용" }).click();
  } else {
    await seller.getByLabel("1번째 줄 가격").fill(String(input.price));
    await seller.getByLabel("1번째 줄 초기 재고").fill(String(input.stock));
  }
  await seller.getByRole("button", { name: "등록" }).click();
  await expect(seller.getByText("상품을 등록했습니다")).toBeVisible();
  return productIdOf(seller);
}

/** 판매자 상품 상세에서 판매를 시작한다. */
export async function publish(seller: Page): Promise<void> {
  await seller.getByRole("button", { name: "판매 시작" }).click();
  await seller.getByRole("alertdialog").getByRole("button", { name: "판매 시작" }).click();
  await expect(seller.getByText("판매를 시작했습니다")).toBeVisible();
}

/** 판매자 상품 상세에서 판매를 멈춘다. */
export async function stop(seller: Page): Promise<void> {
  await seller.getByRole("button", { name: "판매 중지" }).click();
  await seller.getByRole("alertdialog").getByRole("button", { name: "판매 중지" }).click();
  await expect(seller.getByText("판매를 멈췄습니다")).toBeVisible();
}

export function productIdOf(page: Page): string {
  const id = new URL(page.url()).pathname.split("/products/")[1]?.split("/")[0];
  if (!id) {
    throw new Error(`상품 상세가 아니다: ${page.url()}`);
  }
  return id;
}

type SearchResult = { items: { productId: string; inStock: boolean }[]; totalCount: number };

/** 구매자 검색 API(gateway, 토큰 없음)로 찾은 상품 ID. 화면에 보이는 순서와 같다. */
export async function searchIds(keyword: string, sort = "RELEVANCE"): Promise<string[]> {
  const query = new URLSearchParams({ q: keyword, sort });
  const response = await fetch(`${env.gateway}/api/search/v1/products?${query.toString()}`);
  expect(response.status).toBe(200);
  return ((await response.json()) as SearchResult).items.map((item) => item.productId);
}
