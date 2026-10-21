import { describe, expect, it } from "vitest";
import {
  cancelReasonMessage,
  checkoutLines,
  confirmIdempotencyKey,
  paymentErrorMessage,
} from "./checkout";

const A = "0199f000-0000-7000-8000-0000000000a1";
const B = "0199f000-0000-7000-8000-0000000000b2";
const C = "0199f000-0000-7000-8000-0000000000c3";

describe("checkoutLines", () => {
  const sellers = [
    {
      storeName: "길동상회",
      items: [
        {
          skuId: A,
          productName: "반팔 티셔츠",
          optionValues: ["M"],
          price: 19_900,
          quantity: 2,
          status: "AVAILABLE",
        },
        {
          skuId: B,
          productName: "양말",
          optionValues: [],
          price: 3_000,
          quantity: 1,
          status: "SOLD_OUT",
        },
      ],
    },
    {
      storeName: "춘향상점",
      items: [
        {
          skuId: C,
          productName: "청바지",
          optionValues: [],
          price: 39_900,
          quantity: 1,
          status: "AVAILABLE",
        },
      ],
    },
  ];

  it("고른 SKU 중 지금 살 수 있는 줄만 장바구니 순서대로 고른다. 기대 가격은 장바구니가 보여 준 가격이다", () => {
    const lines = checkoutLines(sellers, [C, B, A]);

    expect(lines).toEqual([
      {
        skuId: A,
        storeName: "길동상회",
        productName: "반팔 티셔츠",
        options: "M",
        expectedPrice: 19_900,
        quantity: 2,
        amount: 39_800,
      },
      {
        skuId: C,
        storeName: "춘향상점",
        productName: "청바지",
        options: "",
        expectedPrice: 39_900,
        quantity: 1,
        amount: 39_900,
      },
    ]);
  });

  it("고른 것이 없으면 비어 있다", () => {
    expect(checkoutLines(sellers, [])).toEqual([]);
    expect(checkoutLines(sellers, [B])).toEqual([]);
  });
});

describe("confirmIdempotencyKey", () => {
  it("같은 결제 키면 같은 키다(새로 고침해도 한 번만 승인). 결제 키가 길어도 100자를 넘지 않는다", () => {
    const key = confirmIdempotencyKey("tgen_20261021abc");

    expect(key).toBe(confirmIdempotencyKey("tgen_20261021abc"));
    expect(key).not.toBe(confirmIdempotencyKey("tgen_20261021abd"));
    expect(confirmIdempotencyKey("x".repeat(200)).length).toBeLessThanOrEqual(100);
  });
});

describe("paymentErrorMessage", () => {
  it("토스가 준 문구가 있으면 그대로 쓰고, 우리 코드는 정해 둔 문구다", () => {
    expect(
      paymentErrorMessage("REJECT_CARD_PAYMENT", "한도초과 혹은 잔액부족으로 결제에 실패했습니다."),
    ).toBe("한도초과 혹은 잔액부족으로 결제에 실패했습니다.");
    expect(
      paymentErrorMessage("PAY_PROCESS_CANCELED", "사용자에 의해 결제가 취소되었습니다."),
    ).toBe("결제를 취소했습니다. 다시 결제할 수 있습니다.");
    expect(paymentErrorMessage("PAYMENT_CANCELLED", undefined)).toContain("주문이 취소되어");
    expect(paymentErrorMessage("PAYMENT_NOT_FOUND", undefined)).toContain("다시 결제해 주세요");
    expect(paymentErrorMessage("UNKNOWN_CODE", undefined)).toBe(
      "결제를 완료하지 못했습니다. 다시 시도해 주세요.",
    );
  });

  it("너무 긴 문구는 자른다(주소로 들어오는 값이다)", () => {
    expect(paymentErrorMessage("X", "가".repeat(500)).length).toBeLessThanOrEqual(200);
  });
});

describe("cancelReasonMessage", () => {
  it("취소 사유를 구매자 문구로 바꾼다", () => {
    expect(cancelReasonMessage("STOCK_UNAVAILABLE")).toContain("재고");
    expect(cancelReasonMessage("PAYMENT_TIMEOUT")).toContain("결제 기한");
    expect(cancelReasonMessage("BUYER_CANCELLED")).toContain("취소한 주문");
    expect(cancelReasonMessage(undefined)).toBe("취소된 주문입니다.");
  });
});
