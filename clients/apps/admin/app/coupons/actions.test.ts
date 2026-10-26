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

const { createPlatformCoupon, stopCoupon } = await import("./actions");

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
  name: "오픈 마켓 10%",
  discountType: "RATE",
  discountValue: "10",
  maxDiscount: "5000",
  minOrderAmount: "",
  quantity: "1000",
  issueStartAt: "2099-10-27T10:00",
  issueEndAt: "2099-11-03T10:00",
  validDays: "30",
};

describe("createPlatformCoupon", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("입력이 틀리면 promotion을 부르지 않는다", async () => {
    const state = await createPlatformCoupon(idleState, form({ ...VALID, discountValue: "0" }));

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.discountValue).toBe("할인율은 1~100%입니다.");
  });

  it("관리자 경로로 멱등성 키와 함께 만들고 쿠폰 목록으로 간다", async () => {
    post.mockResolvedValue({
      data: { couponId: COUPON },
      response: new Response(null, { status: 201 }),
    });

    await expect(createPlatformCoupon(idleState, form(VALID))).rejects.toThrow(
      "redirect:/coupons?notice=coupon-created",
    );

    expect(post).toHaveBeenCalledWith("/v1/admin/coupons", {
      headers: { "Idempotency-Key": "key-1" },
      body: {
        name: "오픈 마켓 10%",
        discountType: "RATE",
        discountValue: 10,
        maxDiscount: 5_000,
        minOrderAmount: 0,
        quantity: 1_000,
        issueStartAt: "2099-10-27T01:00:00.000Z",
        issueEndAt: "2099-11-03T01:00:00.000Z",
        validDays: 30,
      },
    });
  });

  it("promotion이 거절하면 문구를 보이고 입력을 남긴다", async () => {
    post.mockResolvedValue({
      error: { code: "PROMOTION_INVALID_INPUT", detail: "발급 기간이 틀렸습니다." },
      response: new Response(null, { status: 400 }),
    });

    const state = await createPlatformCoupon(idleState, form(VALID));

    expect(redirect).not.toHaveBeenCalled();
    expect(state.message).toBe("발급 기간이 틀렸습니다.");
    expect(state.values?.name).toBe("오픈 마켓 10%");
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

  it("판매자 쿠폰도 멈추고, 보던 목록 조건을 지켜 돌아간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      stopCoupon(idleState, form({ couponId: COUPON, returnTo: "/coupons?issuer=SELLER&page=1" })),
    ).rejects.toThrow("redirect:/coupons?issuer=SELLER&page=1&notice=coupon-stopped");

    expect(post).toHaveBeenCalledWith("/v1/admin/coupons/{couponId}/stop", {
      params: { path: { couponId: COUPON } },
    });
  });
});
