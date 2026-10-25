import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();
const getAccessToken = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: {
    getAccessToken: () => getAccessToken(),
    getSession: vi.fn(async () => ({ roles: ["BUYER"] })),
    serviceClient: vi.fn(async () => ({ POST: post })),
  },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { issueCoupon } = await import("./actions");

const COUPON = "0199f000-0000-7000-8000-0000000c0001";

function form(returnTo = "/products/p-1", couponId = COUPON): FormData {
  const data = new FormData();
  data.set("couponId", couponId);
  data.set("returnTo", returnTo);
  return data;
}

function answer(status: number, code?: string) {
  return {
    data: status < 300 ? { issuedCouponId: "i-1" } : undefined,
    error: code ? { code } : undefined,
    response: new Response(null, { status }),
  };
}

describe("issueCoupon", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
    getAccessToken.mockResolvedValue("token");
  });

  it("쿠폰을 받고 돌아갈 화면에 결과를 붙인다. 이미 받은 쿠폰(200)도 받은 것이다", async () => {
    post.mockResolvedValue(answer(201));
    await expect(issueCoupon(form())).rejects.toThrow(
      "redirect:/products/p-1?notice=coupon-issued#coupons",
    );
    expect(post).toHaveBeenCalledWith("/v1/coupons/{couponId}/issue", {
      params: { path: { couponId: COUPON } },
    });

    post.mockResolvedValue(answer(200));
    await expect(issueCoupon(form("/coupons?x=1"))).rejects.toThrow(
      "redirect:/coupons?x=1&notice=coupon-issued#coupons",
    );
  });

  it("소진·발급 중 아님·한도 초과·그 밖의 실패를 구분해 알린다", async () => {
    for (const [status, code, notice] of [
      [409, "PROMOTION_COUPON_SOLD_OUT", "coupon-sold-out"],
      [409, "PROMOTION_COUPON_NOT_ISSUING", "coupon-not-issuing"],
      [404, "PROMOTION_COUPON_NOT_FOUND", "coupon-not-issuing"],
      [429, undefined, "coupon-busy"],
      [503, "PROMOTION_UNAVAILABLE", "coupon-failed"],
    ] as const) {
      post.mockResolvedValueOnce(answer(status, code));
      await expect(issueCoupon(form("/coupons"))).rejects.toThrow(
        `redirect:/coupons?notice=${notice}#coupons`,
      );
    }
  });

  it("로그인하지 않았으면 로그인한 뒤 그 화면으로 돌아온다", async () => {
    getAccessToken.mockResolvedValue(null);
    await expect(issueCoupon(form())).rejects.toThrow(
      `redirect:/auth/login?returnTo=${encodeURIComponent("/products/p-1")}`,
    );
    expect(post).not.toHaveBeenCalled();
  });

  it("돌아갈 화면은 이 앱 안의 경로만 믿고, 쿠폰 ID가 틀리면 부르지 않는다", async () => {
    post.mockResolvedValue(answer(201));
    await expect(issueCoupon(form("//evil.example"))).rejects.toThrow(
      "redirect:/coupons?notice=coupon-issued#coupons",
    );
    await expect(issueCoupon(form("/coupons", "not-uuid"))).rejects.toThrow(
      "redirect:/coupons?notice=coupon-failed#coupons",
    );
    expect(post).toHaveBeenCalledTimes(1);
  });
});
