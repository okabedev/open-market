"use client";

import { FormAlert, idleState, SubmitButton, TextAreaField } from "@openmarket/ui";
import { useActionState, useState } from "react";
import { writeReview } from "@/app/my/reviews/actions";
import { PhotoUpload } from "@/components/photo-upload";
import { REVIEW_LIMITS, stars } from "@/lib/reviews";

type ReviewFormProps = {
  orderLineId: string;
  /** 화면을 그릴 때 만든 멱등성 키. 두 번 눌러도 한 번만 쓴다 */
  idempotencyKey: string;
};

const RATINGS = [5, 4, 3, 2, 1] as const;

function initialPhotos(value: string | undefined): string[] {
  try {
    const parsed: unknown = value ? JSON.parse(value) : [];
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

/**
 * 리뷰 폼(REV-1). 별점은 1~5점 중 하나를 고르고, 본문은 10~1,000자다. 사진은 고르는 즉시 BFF로 올리고(`PhotoUpload`) 파일 ID만 갖는다.
 * 리뷰 사진은 공개라 공개 경로(`/files/{fileId}/w300`)로 미리 본다.
 */
export function ReviewForm({ orderLineId, idempotencyKey }: ReviewFormProps) {
  const [state, formAction] = useActionState(writeReview, idleState);
  const errors = state.fieldErrors ?? {};
  const [photos, setPhotos] = useState(() => initialPhotos(state.values?.fileIds));
  const [uploading, setUploading] = useState(false);

  return (
    <form action={formAction} className="grid gap-5">
      <input type="hidden" name="orderLineId" value={orderLineId} />
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <input type="hidden" name="fileIds" value={JSON.stringify(photos)} />
      <FormAlert state={state} />

      <fieldset
        className="grid gap-2"
        aria-invalid={errors.rating ? true : undefined}
        aria-describedby={errors.rating ? "rating-error" : undefined}
      >
        <legend className="mb-1 text-sm font-medium">별점</legend>
        <div className="flex flex-wrap gap-2">
          {RATINGS.map((rating) => (
            <label
              key={rating}
              className="flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm has-[:checked]:border-primary"
            >
              <input
                type="radio"
                name="rating"
                value={rating}
                defaultChecked={state.values?.rating === String(rating)}
                required
              />
              <span aria-hidden className="text-primary">
                {stars(rating)}
              </span>
              <span>{rating}점</span>
            </label>
          ))}
        </div>
        {errors.rating ? (
          <p id="rating-error" className="text-xs text-destructive">
            {errors.rating}
          </p>
        ) : null}
      </fieldset>

      <TextAreaField
        name="content"
        label="리뷰"
        required
        minLength={REVIEW_LIMITS.contentMin}
        maxLength={REVIEW_LIMITS.contentMax}
        error={errors.content}
        defaultValue={state.values?.content}
        hint={`${REVIEW_LIMITS.contentMin}자 이상 ${REVIEW_LIMITS.contentMax.toLocaleString("ko-KR")}자까지.`}
      />

      <PhotoUpload
        purpose="REVIEW_IMAGE"
        limit={REVIEW_LIMITS.photos}
        photos={photos}
        onPhotosChange={setPhotos}
        onUploadingChange={setUploading}
        previewSrc={(fileId) => `/files/${fileId}/w300`}
        audience="상품 상세에 리뷰와 함께 보입니다."
        error={errors.fileIds}
      />

      <div>
        <SubmitButton disabled={uploading} pendingText="등록하는 중…">
          리뷰 등록하기
        </SubmitButton>
      </div>
    </form>
  );
}
