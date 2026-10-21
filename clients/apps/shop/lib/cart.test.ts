import { describe, expect, it } from "vitest";
import { cartItemStatusLabel, cartSummary } from "./cart";

describe("cartSummary", () => {
  it("구매할 수 있는 줄(AVAILABLE)만 세고, 금액은 개당 가격 × 수량의 합이다", () => {
    const summary = cartSummary([
      {
        items: [
          { status: "AVAILABLE", price: 19_900, quantity: 2 },
          { status: "SOLD_OUT", price: 5_000, quantity: 1 },
        ],
      },
      {
        items: [
          { status: "AVAILABLE", price: 39_900, quantity: 1 },
          { status: "INSUFFICIENT_STOCK", price: 1_000, quantity: 3 },
          { status: "NOT_ON_SALE", price: 2_000, quantity: 1 },
        ],
      },
    ]);

    expect(summary).toEqual({ itemCount: 5, orderableCount: 2, orderableAmount: 79_700 });
  });

  it("비어 있으면 모두 0이다", () => {
    expect(cartSummary([])).toEqual({ itemCount: 0, orderableCount: 0, orderableAmount: 0 });
  });
});

describe("cartItemStatusLabel", () => {
  it("줄 상태를 화면 문구로 바꾼다. 모르는 값은 판매 중지로 본다", () => {
    expect(cartItemStatusLabel("AVAILABLE")).toBe("구매 가능");
    expect(cartItemStatusLabel("INSUFFICIENT_STOCK")).toBe("재고 부족");
    expect(cartItemStatusLabel("SOLD_OUT")).toBe("품절");
    expect(cartItemStatusLabel("NOT_ON_SALE")).toBe("판매 중지");
    expect(cartItemStatusLabel(undefined)).toBe("판매 중지");
  });
});
