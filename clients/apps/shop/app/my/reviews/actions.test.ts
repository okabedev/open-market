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

const { writeReview } = await import("./actions");

const LINE = "0199f5a0-0000-7000-8000-0000000000b1";
const FILE = "0199f5a0-0000-7000-8000-0000000000f1";

function form(fields: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    data.set(name, value);
  }
  return data;
}

const VALID = {
  orderLineId: LINE,
  idempotencyKey: "key-1",
  rating: "5",
  content: "  두께가 적당하고 구김이 적어요.  ",
  fileIds: JSON.stringify([FILE]),
};

describe("writeReview", () => {
  beforeEach(() => {
    post.mockReset();
    redirect.mockClear();
  });

  it("주문 항목 ID가 UUID가 아니면 review를 부르지 않는다", async () => {
    const state = await writeReview(idleState, form({ ...VALID, orderLineId: "../x" }));

    expect(post).not.toHaveBeenCalled();
    expect(state).toEqual({ status: "error", message: "리뷰를 쓸 주문 상품을 찾을 수 없습니다." });
  });

  it("별점, 본문 길이, 사진 수가 틀리면 칸마다 문구를 보이고 입력을 남긴다", async () => {
    const values = {
      rating: "",
      content: " 짧아요 ",
      fileIds: JSON.stringify(Array.from({ length: 6 }, () => FILE)),
    };

    const state = await writeReview(idleState, form({ ...VALID, ...values }));

    expect(post).not.toHaveBeenCalled();
    expect(state).toEqual({
      status: "error",
      fieldErrors: {
        rating: "별점을 골라 주세요.",
        content: "리뷰는 10자 이상 써 주세요.",
        fileIds: "사진은 5장까지 올릴 수 있습니다.",
      },
      values,
    });
  });

  it("본문이 1,000자를 넘으면 쓰지 않는다", async () => {
    const state = await writeReview(idleState, form({ ...VALID, content: "가".repeat(1001) }));

    expect(post).not.toHaveBeenCalled();
    expect(state.fieldErrors?.content).toBe("리뷰는 1,000자까지 쓸 수 있습니다.");
  });

  it("멱등성 키와 함께 쓰고 내 리뷰로 간다. 본문 앞뒤 공백은 지운다", async () => {
    post.mockResolvedValue({
      data: { reviewId: "r-1" },
      response: new Response(null, { status: 201 }),
    });

    await expect(writeReview(idleState, form(VALID))).rejects.toThrow(
      "redirect:/my/reviews?notice=review-written",
    );

    expect(post).toHaveBeenCalledWith("/v1/reviews", {
      headers: { "Idempotency-Key": "key-1" },
      body: {
        orderLineId: LINE,
        rating: 5,
        content: "두께가 적당하고 구김이 적어요.",
        fileIds: [FILE],
      },
    });
  });

  it("이미 썼거나 쓸 수 없는 주문 상품이면 review 문구를 보이고 입력을 남긴다", async () => {
    post.mockResolvedValue({
      error: { code: "REVIEW_ALREADY_WRITTEN", detail: "이미 리뷰를 쓴 주문 상품입니다." },
      response: new Response(null, { status: 409 }),
    });

    const state = await writeReview(idleState, form(VALID));

    expect(redirect).not.toHaveBeenCalled();
    expect(state).toEqual({
      status: "error",
      message: "이미 리뷰를 쓴 주문 상품입니다.",
      values: { rating: "5", content: VALID.content, fileIds: VALID.fileIds },
    });
  });
});
