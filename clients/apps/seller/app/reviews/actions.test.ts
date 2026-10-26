import { idleState } from "@openmarket/ui";
import { beforeEach, describe, expect, it, vi } from "vitest";

const put = vi.fn();
vi.mock("@/lib/bff", () => ({
  bff: { serviceClient: vi.fn(async () => ({ PUT: put })) },
}));
const redirect = vi.fn((url: string) => {
  throw new Error(`redirect:${url}`);
});
vi.mock("next/navigation", () => ({ redirect: (url: string) => redirect(url) }));

const { writeReply } = await import("./actions");

const REVIEW = "0199f5a0-0000-7000-8000-0000000000e1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

describe("writeReply", () => {
  beforeEach(() => {
    put.mockReset();
    redirect.mockClear();
  });

  it("리뷰 ID가 UUID가 아니면 review를 부르지 않는다", async () => {
    const state = await writeReply(idleState, form({ reviewId: "../x", content: "감사합니다." }));

    expect(put).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "리뷰를 찾을 수 없습니다." });
  });

  it("본문이 비었거나 1,000자를 넘으면 쓰지 않고 입력을 남긴다", async () => {
    const empty = await writeReply(idleState, form({ reviewId: REVIEW, content: "   " }));
    const long = await writeReply(
      idleState,
      form({ reviewId: REVIEW, content: "가".repeat(1001) }),
    );

    expect(put).not.toHaveBeenCalled();
    expect(empty).toEqual({
      status: "error",
      fieldErrors: { content: "답글을 써 주세요." },
      values: { content: "   " },
    });
    expect(long.fieldErrors?.content).toBe("답글은 1,000자까지 쓸 수 있습니다.");
  });

  it("앞뒤 공백을 지워 답글을 달고 그 리뷰로 돌아간다", async () => {
    put.mockResolvedValue({ response: new Response(null, { status: 204 }) });

    await expect(
      writeReply(idleState, form({ reviewId: REVIEW, content: "  찾아 주셔서 감사합니다. " })),
    ).rejects.toThrow(`redirect:/reviews?notice=replied#review-${REVIEW}`);

    expect(put).toHaveBeenCalledWith("/v1/seller/reviews/{reviewId}/reply", {
      params: { path: { reviewId: REVIEW } },
      body: { content: "찾아 주셔서 감사합니다." },
    });
  });

  it("다른 판매자의 리뷰면 review 문구를 보이고 입력을 남긴다", async () => {
    put.mockResolvedValue({
      error: { code: "REVIEW_NOT_FOUND", detail: "리뷰를 찾을 수 없습니다." },
      response: new Response(null, { status: 404 }),
    });

    const state = await writeReply(idleState, form({ reviewId: REVIEW, content: "감사합니다." }));

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({
      status: "error",
      message: "리뷰를 찾을 수 없습니다.",
      values: { content: "감사합니다." },
    });
  });
});
