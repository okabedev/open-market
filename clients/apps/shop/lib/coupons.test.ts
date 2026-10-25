import { describe, expect, it } from "vitest";
import {
  couponChoices,
  couponTerms,
  discountSummary,
  issueNotice,
  selectedCouponIds,
  sellerOrderAmounts,
  stateLabel,
  unavailableMessage,
} from "./coupons";

const SELLER_A = "0199f000-0000-7000-8000-00000000a001";
const SELLER_B = "0199f000-0000-7000-8000-00000000a002";
const SELLER_C = "0199f000-0000-7000-8000-00000000a003";

const coupon = (id: string, sellerId?: string) => ({
  issuedCouponId: id,
  issuer: sellerId ? ("SELLER" as const) : ("PLATFORM" as const),
  ...(sellerId ? { sellerId } : {}),
  name: `쿠폰 ${id}`,
  discountType: "FIXED" as const,
  discountValue: 1_000,
  minOrderAmount: 0,
});

describe("couponTerms", () => {
  it("정액·정률 할인과 최대 할인, 최소 주문 금액을 쓴다", () => {
    expect(couponTerms({ discountType: "FIXED", discountValue: 3_000, minOrderAmount: 0 })).toBe(
      "3,000원 할인",
    );
    expect(
      couponTerms({
        discountType: "RATE",
        discountValue: 10,
        maxDiscount: 5_000,
        minOrderAmount: 30_000,
      }),
    ).toBe("10% 할인(최대 5,000원) · 30,000원 이상 구매 시");
  });
});

describe("stateLabel", () => {
  it("내 쿠폰 상태를 화면 이름으로 바꾼다", () => {
    expect(stateLabel("USABLE")).toBe("사용 가능");
    expect(stateLabel("RESERVED")).toBe("주문에 사용 중");
    expect(stateLabel("USED")).toBe("사용함");
    expect(stateLabel("EXPIRED")).toBe("기간 만료");
  });
});

describe("sellerOrderAmounts", () => {
  it("주문서 줄을 판매자별 상품 금액으로 묶는다(처음 나온 순서)", () => {
    expect(
      sellerOrderAmounts([
        { sellerId: SELLER_A, amount: 10_000 },
        { sellerId: SELLER_B, amount: 5_000 },
        { sellerId: SELLER_A, amount: 2_000 },
      ]),
    ).toEqual([
      { sellerId: SELLER_A, amount: 12_000 },
      { sellerId: SELLER_B, amount: 5_000 },
    ]);
  });
});

describe("couponChoices", () => {
  it("플랫폼 쿠폰과 주문에 있는 판매자의 쿠폰만 고를 수 있다", () => {
    const choices = couponChoices(
      [coupon("p1"), coupon("a1", SELLER_A), coupon("c1", SELLER_C), coupon("a2", SELLER_A)],
      [SELLER_A, SELLER_B],
    );
    expect(choices.platform.map((c) => c.issuedCouponId)).toEqual(["p1"]);
    expect(choices.bySeller.get(SELLER_A)?.map((c) => c.issuedCouponId)).toEqual(["a1", "a2"]);
    expect(choices.bySeller.get(SELLER_B)).toEqual([]);
    expect(choices.bySeller.has(SELLER_C)).toBe(false);
  });
});

describe("selectedCouponIds", () => {
  const choices = couponChoices(
    [coupon("p1"), coupon("a1", SELLER_A), coupon("b1", SELLER_B)],
    [SELLER_A, SELLER_B],
  );

  it("주소의 고른 쿠폰 중 고를 수 있는 것만 플랫폼, 판매자 순으로 쓴다", () => {
    expect(
      selectedCouponIds({ platformCoupon: "p1", sellerCoupon: ["b1", "a1", "x9"] }, choices),
    ).toEqual(["p1", "b1", "a1"]);
  });

  it("판매자마다 한 장, 플랫폼 한 장이다. 같은 판매자 쿠폰을 둘 주면 앞의 것만 쓴다", () => {
    const many = couponChoices([coupon("a1", SELLER_A), coupon("a2", SELLER_A)], [SELLER_A]);
    expect(selectedCouponIds({ sellerCoupon: ["a2", "a1"] }, many)).toEqual(["a2"]);
    expect(selectedCouponIds({ platformCoupon: ["p1", "p2"] }, choices)).toEqual([]);
    expect(selectedCouponIds({}, choices)).toEqual([]);
  });
});

describe("discountSummary", () => {
  it("판매자 쿠폰 할인과 플랫폼 쿠폰 할인을 더하고 결제 금액을 준다", () => {
    expect(
      discountSummary({
        sellerOrders: [
          { sellerId: SELLER_A, sellerCouponDiscount: 3_000 },
          { sellerId: SELLER_B, sellerCouponDiscount: 0 },
        ],
        platformCouponDiscount: 2_700,
        paymentAmount: 24_300,
      }),
    ).toEqual({ sellerDiscount: 3_000, platformDiscount: 2_700, paymentAmount: 24_300 });
  });
});

describe("unavailableMessage", () => {
  it("쓸 수 없는 이유를 구매자 문구로 바꾼다", () => {
    expect(unavailableMessage("MIN_ORDER_AMOUNT_NOT_MET")).toBe("최소 주문 금액이 모자랍니다.");
    expect(unavailableMessage("EXPIRED")).toBe("사용 기간이 지났습니다.");
    expect(unavailableMessage("NOT_AVAILABLE")).toBe(
      "다른 주문에 사용 중이거나 이미 사용했습니다.",
    );
    expect(unavailableMessage("SOMETHING_NEW")).toBe("쓸 수 없는 쿠폰입니다.");
  });
});

describe("issueNotice", () => {
  it("쿠폰 받기 결과를 안내한다. 모르는 값은 없다", () => {
    expect(issueNotice("coupon-issued")).toEqual({
      variant: "success",
      text: "쿠폰을 받았습니다. 내 쿠폰에서 확인할 수 있습니다.",
    });
    expect(issueNotice("coupon-sold-out")?.text).toBe("쿠폰이 모두 소진됐습니다.");
    expect(issueNotice("coupon-not-issuing")?.text).toBe("지금은 받을 수 없는 쿠폰입니다.");
    expect(issueNotice("coupon-busy")?.text).toBe(
      "요청이 많아 받지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
    );
    expect(issueNotice("coupon-failed")?.variant).toBe("destructive");
    expect(issueNotice("other")).toBeNull();
    expect(issueNotice(undefined)).toBeNull();
  });
});
