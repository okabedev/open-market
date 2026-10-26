import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ POST: post })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { createCoupon, stopCoupon } = await import("./actions");

const COUPON = "0199f5a0-0000-7000-8000-0000000000c1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

const VALID = {
  idempotencyKey: "key-1",
  name: "가을 맞이 3천 원",
  discountType: "FIXED",
  discountValue: "3000",
  maxDiscount: "",
  minOrderAmount: "10000",
  quantity: "100",
  issueStartAt: "2099-10-27T10:00",
  issueEndAt: "2099-11-03T10:00",
  validDays: "14",
};

describe("createCoupon", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("입력이 틀리면 promotion을 부르지 않는다", async () => {
    const state = await createCoupon(idleState, form({ ...VALID, quantity: "0" }));

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.quantity).toBe("발급 수량은 1~1,000,000장입니다.");
  });

  it("멱등성 키와 함께 만들고 쿠폰 목록으로 간다", async () => {
    post.mockResolvedValue({
      data: { couponId: COUPON },
      response: new Response(null, { status: 201 }),
    });

    await expect(createCoupon(idleState, form(VALID))).rejects.toThrow(
      "redirect:/coupons?notice=created",
    );

    expect(post).toHaveBeenCalledWith("/v1/seller/coupons", {
      headers: { "Idempotency-Key": "key-1" },
      body: {
        name: "가을 맞이 3천 원",
        discountType: "FIXED",
        discountValue: 3_000,
        minOrderAmount: 10_000,
        quantity: 100,
        issueStartAt: "2099-10-27T01:00:00.000Z",
        issueEndAt: "2099-11-03T01:00:00.000Z",
        validDays: 14,
      },
    });
  });

  it("promotion이 거절하면 문구를 보이고 입력을 남긴다", async () => {
    post.mockResolvedValue({
      error: { code: "PROMOTION_INVALID_INPUT", detail: "발급 기간이 틀렸습니다." },
      response: new Response(null, { status: 400 }),
    });

    const state = await createCoupon(idleState, form(VALID));

    expect(redirect).not.toHaveBeenCalled();
    expect(state.message).toBe("발급 기간이 틀렸습니다.");
    expect(state.values?.name).toBe("가을 맞이 3천 원");
  });
});

describe("stopCoupon", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("쿠폰 ID가 UUID가 아니면 부르지 않는다", async () => {
    const state = await stopCoupon(idleState, form({ couponId: "../x" }));

    expect(post).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "쿠폰을 찾을 수 없습니다." });
  });

  it("발급을 멈추고 쿠폰 목록으로 간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(stopCoupon(idleState, form({ couponId: COUPON }))).rejects.toThrow(
      "redirect:/coupons?notice=stopped",
    );

    expect(post).toHaveBeenCalledWith("/v1/seller/coupons/{couponId}/stop", {
      params: { path: { couponId: COUPON } },
    });
  });

  it("다른 판매자의 쿠폰이면 promotion 문구를 보인다", async () => {
    post.mockResolvedValue({
      error: { code: "PROMOTION_COUPON_NOT_FOUND", detail: "쿠폰을 찾을 수 없습니다." },
      response: new Response(null, { status: 404 }),
    });

    const state = await stopCoupon(idleState, form({ couponId: COUPON }));

    expect(state).toEqual({ status: "error", message: "쿠폰을 찾을 수 없습니다." });
  });
});
