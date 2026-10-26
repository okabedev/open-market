import { describe, expect, it } from "vitest";
import { readCouponForm } from "./coupon-form";

const NOW = new Date("2026-10-27T00:00:00Z"); // 한국 시간 2026-10-27 09:00

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

const valid = {
  name: "  가을 맞이 3천 원 ",
  discountType: "FIXED",
  discountValue: "3000",
  maxDiscount: "",
  minOrderAmount: "10000",
  quantity: "100",
  issueStartAt: "2026-10-27T10:00",
  issueEndAt: "2026-11-03T10:00",
  validDays: "14",
};

describe("readCouponForm", () => {
  it("한국 시간 입력을 ISO로 바꾸고 정액이면 최대 할인을 보내지 않는다", () => {
    expect(readCouponForm(form({ ...valid, maxDiscount: "5000" }), NOW)).toEqual({
      success: true,
      data: {
        name: "가을 맞이 3천 원",
        discountType: "FIXED",
        discountValue: 3_000,
        minOrderAmount: 10_000,
        quantity: 100,
        issueStartAt: "2026-10-27T01:00:00.000Z",
        issueEndAt: "2026-11-03T01:00:00.000Z",
        validDays: 14,
      },
    });
  });

  it("정률은 최대 할인을 함께 보낸다. 최대 할인과 최소 주문 금액은 비워도 된다(최소 주문 금액은 0)", () => {
    const rate = readCouponForm(
      form({ ...valid, discountType: "RATE", discountValue: "10", maxDiscount: "5000" }),
      NOW,
    );
    expect(rate.success && rate.data).toMatchObject({
      discountType: "RATE",
      discountValue: 10,
      maxDiscount: 5_000,
    });

    const open = readCouponForm(
      form({ ...valid, discountType: "RATE", discountValue: "10", minOrderAmount: "" }),
      NOW,
    );
    expect(open.success && open.data).toMatchObject({ minOrderAmount: 0 });
    expect(open.success && "maxDiscount" in open.data).toBe(false);
  });

  it("발급 시작은 지나도 된다(바로 발급)", () => {
    const started = readCouponForm(form({ ...valid, issueStartAt: "2026-10-20T10:00" }), NOW);

    expect(started.success).toBe(true);
  });

  it("규칙을 어긴 칸마다 문구를 보이고 입력을 남긴다", () => {
    const values = {
      ...valid,
      name: " ",
      discountType: "RATE",
      discountValue: "101",
      quantity: "0",
      issueStartAt: "2026-11-03T10:00",
      issueEndAt: "2026-11-03T10:00",
      validDays: "366",
    };

    const result = readCouponForm(form(values), NOW);

    expect(result).toEqual({
      success: false,
      state: {
        status: "error",
        message: "입력을 확인해 주세요.",
        fieldErrors: {
          name: "쿠폰 이름을 1~50자로 써 주세요.",
          discountValue: "할인율은 1~100%입니다.",
          quantity: "발급 수량은 1~1,000,000장입니다.",
          issueEndAt: "발급 끝은 시작보다 뒤여야 합니다.",
          validDays: "사용 기한은 발급 후 1~365일입니다.",
        },
        values,
      },
    });
  });

  it("발급 끝이 지났거나 시각이 비면 막는다. 정액은 1원 이상이다", () => {
    const result = readCouponForm(
      form({
        ...valid,
        discountValue: "0",
        issueStartAt: "",
        issueEndAt: "2026-10-27T08:59",
      }),
      NOW,
    );

    expect(result.success).toBe(false);
    expect(!result.success && result.state.fieldErrors).toEqual({
      discountValue: "할인 금액은 1원 이상입니다.",
      issueStartAt: "발급 시작 시각을 골라 주세요.",
      issueEndAt: "발급 끝은 지금보다 뒤여야 합니다.",
    });
  });
});
