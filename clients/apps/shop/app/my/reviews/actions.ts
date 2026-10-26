"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { text } from "@/lib/contact";
import { REVIEW_LIMITS } from "@/lib/reviews";

const reviewSchema = z.object({
  rating: z.coerce
    .number({ error: "별점을 골라 주세요." })
    .int("별점을 골라 주세요.")
    .min(1, "별점을 골라 주세요.")
    .max(5, "별점을 골라 주세요."),
  content: z
    .string()
    .trim()
    .min(REVIEW_LIMITS.contentMin, `리뷰는 ${REVIEW_LIMITS.contentMin}자 이상 써 주세요.`)
    .max(
      REVIEW_LIMITS.contentMax,
      `리뷰는 ${REVIEW_LIMITS.contentMax.toLocaleString("ko-KR")}자까지 쓸 수 있습니다.`,
    ),
  fileIds: z
    .array(z.uuid(), { error: "사진을 다시 올려 주세요." })
    .max(REVIEW_LIMITS.photos, `사진은 ${REVIEW_LIMITS.photos}장까지 올릴 수 있습니다.`),
});

function fileIds(value: string): unknown {
  try {
    return value ? JSON.parse(value) : [];
  } catch {
    return null;
  }
}

/**
 * 구매확정한 주문 상품의 리뷰를 쓴다(REV-1). 사진은 미리 BFF(`/api/files?purpose=REVIEW_IMAGE`)로 올린 파일 ID다. 화면을 그릴 때 만든
 * 멱등성 키를 보내 두 번 눌러도 한 번만 쓴다. 이미 썼거나(409) 쓸 수 없는 주문 상품(404)이면 review 문구를 보이고 입력을 남긴다. 쓰면 내
 * 리뷰로 간다.
 */
export async function writeReview(_: FormState, form: FormData): Promise<FormState> {
  const orderLineId = z.uuid().safeParse(text(form, "orderLineId"));
  if (!orderLineId.success) {
    return { status: "error", message: "리뷰를 쓸 주문 상품을 찾을 수 없습니다." };
  }
  const values = {
    rating: text(form, "rating"),
    content: text(form, "content"),
    fileIds: text(form, "fileIds"),
  };
  const parsed = reviewSchema.safeParse({
    // 고르지 않은 별점(빈 값)이 0으로 바뀌어 범위 문구가 나오지 않도록 그대로 넘긴다.
    rating: values.rating === "" ? undefined : values.rating,
    content: values.content,
    fileIds: fileIds(values.fileIds),
  });
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const review = await bff.serviceClient("review");
  const { error, response } = await review.POST("/v1/reviews", {
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
    body: { orderLineId: orderLineId.data, ...parsed.data },
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  redirect("/my/reviews?notice=review-written");
}
