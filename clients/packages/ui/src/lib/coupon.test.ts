import { describe, expect, it } from "vitest";
import { couponPhase, couponTerms, kstInputToIso, toKstInput } from "./coupon";

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

describe("couponPhase", () => {
  const coupon = {
    status: "ISSUING" as const,
    issueStartAt: "2026-10-27T01:00:00Z",
    issueEndAt: "2026-11-03T01:00:00Z",
  };

  it("발급 기간 전·안·끝을 지금 시각으로 정한다. 끝 시각부터는 끝남이다", () => {
    expect(couponPhase(coupon, new Date("2026-10-27T00:59:59Z"))).toBe("SCHEDULED");
    expect(couponPhase(coupon, new Date("2026-10-27T01:00:00Z"))).toBe("ISSUING");
    expect(couponPhase(coupon, new Date("2026-11-03T01:00:00Z"))).toBe("ENDED");
  });

  it("중지한 쿠폰은 기간과 상관없이 중지다", () => {
    expect(couponPhase({ ...coupon, status: "STOPPED" }, new Date("2026-10-28T00:00:00Z"))).toBe(
      "STOPPED",
    );
  });
});

describe("kstInputToIso", () => {
  it("datetime-local 값을 한국 시간으로 읽어 ISO로 바꾼다", () => {
    expect(kstInputToIso("2026-10-27T10:00")).toBe("2026-10-27T01:00:00.000Z");
    expect(kstInputToIso("2026-10-27T00:30")).toBe("2026-10-26T15:30:00.000Z");
  });

  it("형식이 틀리거나 없는 날짜면 null이다", () => {
    expect(kstInputToIso("")).toBeNull();
    expect(kstInputToIso("2026-10-27")).toBeNull();
    expect(kstInputToIso("2026-02-30T10:00")).toBeNull();
    expect(kstInputToIso("2026-10-27T25:00")).toBeNull();
  });
});

describe("toKstInput", () => {
  it("시각을 한국 시간의 datetime-local 값으로 쓴다", () => {
    expect(toKstInput(new Date("2026-10-27T01:00:00Z"))).toBe("2026-10-27T10:00");
    expect(toKstInput(new Date("2026-10-26T15:30:59Z"))).toBe("2026-10-27T00:30");
  });
});
