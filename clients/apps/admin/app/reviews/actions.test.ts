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

const { hideReview, showReview } = await import("./actions");

const REVIEW = "0199f5a0-0000-7000-8000-0000000000e1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

describe("hideReview", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("리뷰 ID가 UUID가 아니면 review를 부르지 않는다", async () => {
    const state = await hideReview(idleState, form({ reviewId: "../x", reason: "욕설" }));

    expect(post).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "리뷰를 찾을 수 없습니다." });
  });

  it("사유가 없거나 200자를 넘으면 숨기지 않고 입력을 남긴다", async () => {
    const empty = await hideReview(idleState, form({ reviewId: REVIEW, reason: "  " }));
    const long = await hideReview(idleState, form({ reviewId: REVIEW, reason: "가".repeat(201) }));

    expect(post).not.toHaveBeenCalled();
    expect(empty).toEqual({
      status: "error",
      fieldErrors: { reason: "사유를 입력해 주세요." },
      values: { reason: "  " },
    });
    expect(long.fieldErrors?.reason).toBe("사유는 200자까지입니다.");
  });

  it("사유와 함께 숨기고 보던 목록 조건으로 돌아간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      hideReview(
        idleState,
        form({
          reviewId: REVIEW,
          reason: " 개인정보 노출 ",
          returnTo: "/reviews?status=VISIBLE&page=2",
        }),
      ),
    ).rejects.toThrow("redirect:/reviews?status=VISIBLE&page=2&notice=review-hidden");

    expect(post).toHaveBeenCalledWith("/v1/admin/reviews/{reviewId}/hide", {
      params: { path: { reviewId: REVIEW } },
      body: { reason: "개인정보 노출" },
    });
  });
});

describe("showReview", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("사유와 함께 다시 보이고 목록으로 돌아간다", async () => {
    post.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      showReview(idleState, form({ reviewId: REVIEW, reason: "오신고 확인" })),
    ).rejects.toThrow("redirect:/reviews?notice=review-shown");

    expect(post).toHaveBeenCalledWith("/v1/admin/reviews/{reviewId}/show", {
      params: { path: { reviewId: REVIEW } },
      body: { reason: "오신고 확인" },
    });
  });

  it("없는 리뷰면 review 문구를 보인다", async () => {
    post.mockResolvedValue({
      error: { code: "REVIEW_NOT_FOUND", detail: "리뷰를 찾을 수 없습니다." },
      response: new Response(null, { status: 404 }),
    });

    const state = await showReview(idleState, form({ reviewId: REVIEW, reason: "오신고 확인" }));

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "리뷰를 찾을 수 없습니다." });
  });
});
