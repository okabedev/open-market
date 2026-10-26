"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { REPLY_MAX } from "@/lib/review";

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}

const replySchema = z.object({
  content: z
    .string()
    .trim()
    .min(1, "답글을 써 주세요.")
    .max(REPLY_MAX, `답글은 ${REPLY_MAX.toLocaleString("ko-KR")}자까지 쓸 수 있습니다.`),
});

/**
 * 내 상품 리뷰에 답글을 달거나 고친다(REV-3). 리뷰마다 하나이고 다시 쓰면 고친다. 같은 본문이면 바뀌지 않아 다시 보내도 결과가 같다
 * (docs/architecture/review.md "답글"). 쓰면 목록의 그 리뷰로 돌아간다. 다른 판매자의 리뷰면 404 문구를 보인다.
 */
export async function writeReply(_: FormState, form: FormData): Promise<FormState> {
  const reviewId = z.uuid().safeParse(text(form, "reviewId"));
  if (!reviewId.success) {
    return { status: "error", message: "리뷰를 찾을 수 없습니다." };
  }
  const values = { content: text(form, "content") };
  const parsed = replySchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const review = await bff.serviceClient("review");
  const { error, response } = await review.PUT("/v1/seller/reviews/{reviewId}/reply", {
    params: { path: { reviewId: reviewId.data } },
    body: parsed.data,
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  redirect(`/reviews?notice=replied#review-${reviewId.data}`);
}
