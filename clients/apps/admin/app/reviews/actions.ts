"use server";

import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { reviewResult, type Notice } from "@/lib/review";

/** 숨김·다시 보임 사유. review 규칙은 1~200자다(docs/architecture/review.md "숨김"). 사유는 감사 로그에만 남는다. */
const REVIEW_REASON_MAX = 200;
const reviewReasonSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, "사유를 입력해 주세요.")
    .max(REVIEW_REASON_MAX, `사유는 ${REVIEW_REASON_MAX}자까지입니다.`),
});

type VisibilityPath = "/v1/admin/reviews/{reviewId}/hide" | "/v1/admin/reviews/{reviewId}/show";

async function changeVisibility(
  form: FormData,
  path: VisibilityPath,
  notice: Notice,
): Promise<FormState> {
  const reviewId = z.uuid().safeParse(form.get("reviewId"));
  if (!reviewId.success) {
    return { status: "error", message: "리뷰를 찾을 수 없습니다." };
  }
  const values = { reason: String(form.get("reason") ?? "") };
  const parsed = reviewReasonSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const review = await bff.serviceClient("review");
  const { error, response } = await review.POST(path, {
    params: { path: { reviewId: reviewId.data } },
    body: parsed.data,
  });
  return reviewResult(error, response, form, "/reviews", notice);
}

/**
 * 리뷰를 숨긴다(REV-3, ADM-2). 상품 리뷰 목록과 별점 요약에서 빠지고 작성자에게는 "숨김"으로 보인다. 이미 숨겼으면 그대로 성공이다.
 * review가 감사 로그(`REVIEW_HIDDEN`)에 사유를 남긴다.
 */
export async function hideReview(_: FormState, form: FormData): Promise<FormState> {
  return changeVisibility(form, "/v1/admin/reviews/{reviewId}/hide", "review-hidden");
}

/** 숨긴 리뷰를 다시 보인다. 별점 요약에 다시 들어간다. 감사 로그는 `REVIEW_SHOWN`이다. */
export async function showReview(_: FormState, form: FormData): Promise<FormState> {
  return changeVisibility(form, "/v1/admin/reviews/{reviewId}/show", "review-shown");
}
