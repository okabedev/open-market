/**
 * 상품 폼의 상한과 옵션 조합 계산. 클라이언트 폼도 쓰므로 zod에 의존하지 않는다(검증 스키마는 product-form.ts). 상한의 원본은
 * catalog 규칙이다(docs/architecture/catalog.md "규칙").
 */
export const PRODUCT_LIMITS = {
  images: 10,
  options: 2,
  skus: 100,
  minPrice: 100,
  maxPrice: 100_000_000,
  maxStock: 999_999,
  imageBytes: 10 * 1024 * 1024,
} as const;

/** 옵션 값 목록들의 모든 조합(데카르트 곱). 옵션 순서대로다. 옵션이 없으면 빈 조합 하나다. */
export function combineOptions(valuesPerOption: readonly (readonly string[])[]): string[][] {
  return valuesPerOption.reduce<string[][]>(
    (combinations, values) =>
      combinations.flatMap((prefix) => values.map((value) => [...prefix, value])),
    [[]],
  );
}

/** "블랙, 화이트"처럼 쉼표로 적은 옵션 값. 공백과 빈 값, 중복을 지운다. */
export function parseOptionValues(input: string): string[] {
  return [
    ...new Set(
      input
        .split(",")
        .map((value) => value.trim())
        .filter((value) => value.length > 0),
    ),
  ];
}

/** 폼의 SKU 한 줄. 가격·재고는 입력 중인 문자열이다. `skuId`가 있으면 이미 있는 SKU(수정)다. */
export type SkuRow = {
  key: string;
  skuId?: string;
  optionValues: string[];
  price: string;
  initialStock: string;
};

let rowSeed = 0;

/**
 * SKU 줄을 만든다. 서버 페이지(초기값)와 클라이언트 폼(조합 만들기)이 함께 쓴다. 클라이언트 모듈(`"use client"`)의 함수는 서버에서
 * 부를 수 없으므로 여기에 둔다. `key`는 목록 안에서만 겹치지 않으면 된다.
 */
export function newRow(
  optionValues: string[],
  skuId?: string,
  price = "",
  initialStock = "0",
): SkuRow {
  rowSeed += 1;
  return {
    key: skuId ?? `new-${rowSeed}`,
    ...(skuId ? { skuId } : {}),
    optionValues,
    price,
    initialStock,
  };
}
