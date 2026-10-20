import { describe, expect, it } from "vitest";
import {
  combineOptions,
  parseOptionValues,
  readProductForm,
  stockAdjustmentSchema,
} from "./product-form";

const CATEGORY = "0199f000-0000-7000-8000-000000000101";
const IMAGE = "0199f5a0-0000-7000-8000-0000000000f1";

function form(fields: Record<string, unknown>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, typeof value === "string" ? value : JSON.stringify(value));
  }
  return data;
}

const valid = {
  categoryId: CATEGORY,
  name: "  반팔 티셔츠 ",
  description: "면 100%",
  imageFileIds: [IMAGE],
  optionNames: ["색상", "사이즈"],
  skus: [
    { optionValues: ["블랙", "M"], price: 19_900, initialStock: 5 },
    { optionValues: ["블랙", "L"], price: 21_900, initialStock: 0 },
  ],
};

describe("combineOptions", () => {
  it("옵션 값의 모든 조합을 옵션 순서대로 만든다", () => {
    expect(
      combineOptions([
        ["블랙", "화이트"],
        ["M", "L"],
      ]),
    ).toEqual([
      ["블랙", "M"],
      ["블랙", "L"],
      ["화이트", "M"],
      ["화이트", "L"],
    ]);
  });

  it("옵션이 없으면 빈 조합 하나다", () => {
    expect(combineOptions([])).toEqual([[]]);
  });
});

describe("parseOptionValues", () => {
  it("쉼표로 나누고 공백·빈 값·중복을 지운다", () => {
    expect(parseOptionValues(" 블랙, 화이트 ,,블랙 ")).toEqual(["블랙", "화이트"]);
  });
});

describe("readProductForm", () => {
  it("올바른 입력은 앞뒤 공백을 지워 서비스 요청 본문으로 만든다", () => {
    const result = readProductForm(form(valid));

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.name).toBe("반팔 티셔츠");
      expect(result.data.skus).toHaveLength(2);
    }
  });

  it("가격은 100원~1억 원, 10원 단위이고 재고는 0~999,999다", () => {
    const result = readProductForm(
      form({
        ...valid,
        skus: [
          { optionValues: ["블랙", "M"], price: 19_905, initialStock: 1 },
          { optionValues: ["블랙", "L"], price: 50, initialStock: 1_000_000 },
        ],
      }),
    );

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.state.fieldErrors).toMatchObject({
        "skus.0.price": "가격은 10원 단위입니다.",
        "skus.1.price": "가격은 100원부터 1억 원까지입니다.",
        "skus.1.initialStock": "재고는 0부터 999,999까지입니다.",
      });
      expect(result.state.values?.name).toBe("  반팔 티셔츠 ");
    }
  });

  it("옵션 값 수가 옵션 이름 수와 다르거나 조합이 겹치면 안 된다", () => {
    const result = readProductForm(
      form({
        ...valid,
        skus: [
          { optionValues: ["블랙", "M"], price: 19_900, initialStock: 1 },
          { optionValues: ["블랙", "M"], price: 19_900, initialStock: 1 },
          { optionValues: ["블랙"], price: 19_900, initialStock: 1 },
        ],
      }),
    );

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(Object.values(result.state.fieldErrors ?? {})).toEqual(
        expect.arrayContaining(["옵션 조합이 겹칩니다.", "옵션마다 값을 하나씩 넣어 주세요."]),
      );
    }
  });

  it("이름·설명·카테고리가 없으면 그 필드 오류를 준다. 깨진 JSON도 오류다", () => {
    const result = readProductForm(
      form({ ...valid, name: " ", description: "", categoryId: "", skus: "{" }),
    );

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.state.fieldErrors).toMatchObject({
        name: "상품 이름을 입력해 주세요.",
        description: "상품 설명을 입력해 주세요.",
        categoryId: "카테고리를 골라 주세요.",
        skus: "옵션 조합을 하나 이상 넣어 주세요.",
      });
    }
  });

  it("이미지는 10장까지다", () => {
    const images = Array.from(
      { length: 11 },
      (_, i) => `0199f5a0-0000-7000-8000-0000000000${(10 + i).toString()}`,
    );
    const result = readProductForm(form({ ...valid, imageFileIds: images }));

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.state.fieldErrors?.imageFileIds).toBe("이미지는 10장까지입니다.");
    }
  });
});

describe("stockAdjustmentSchema", () => {
  it("증감 수량은 0이 아닌 정수이고 사유는 선택이다", () => {
    expect(stockAdjustmentSchema.safeParse({ delta: "5", reason: "" }).success).toBe(true);
    expect(stockAdjustmentSchema.safeParse({ delta: "0", reason: "" }).success).toBe(false);
    expect(stockAdjustmentSchema.safeParse({ delta: "1.5", reason: "" }).success).toBe(false);
  });
});
