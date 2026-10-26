"use client";

import {
  CLAIM_REASON_LABELS,
  FormAlert,
  idleState,
  Label,
  SubmitButton,
  TextAreaField,
} from "@openmarket/ui";
import { useActionState, useState } from "react";
import { PhotoUpload } from "@/components/photo-upload";
import { CLAIM_LIMITS, reasonsFor, type ClaimType } from "@/lib/claim";
import { requestClaim } from "../../actions";

type ClaimFormProps = {
  orderId: string;
  sellerOrderId: string;
  /** 화면을 그릴 때 만든 멱등성 키. 두 번 눌러도 한 번만 신청된다 */
  idempotencyKey: string;
};

const TYPE_LABELS: Record<ClaimType, { title: string; description: string }> = {
  RETURN: { title: "반품", description: "상품을 돌려보내고 환불받습니다." },
  EXCHANGE: {
    title: "교환",
    description: "같은 상품으로 다시 받습니다. 상품 불량·오배송·정보와 다를 때만 됩니다.",
  },
};

function initialPhotos(value: string | undefined): string[] {
  try {
    const parsed: unknown = value ? JSON.parse(value) : [];
    return Array.isArray(parsed) ? parsed.filter((id): id is string => typeof id === "string") : [];
  } catch {
    return [];
  }
}

/**
 * 반품·교환 신청 폼(CLM-2). 사진은 고르는 즉시 BFF로 올리고 파일 ID만 갖는다(`PhotoUpload`). 단순 변심 반품은 반품 배송비를 빼고
 * 환불된다는 것을 미리 알린다.
 */
export function ClaimForm({ orderId, sellerOrderId, idempotencyKey }: ClaimFormProps) {
  const [state, formAction] = useActionState(requestClaim, idleState);
  const errors = state.fieldErrors ?? {};
  const [type, setType] = useState<ClaimType>(
    state.values?.type === "EXCHANGE" ? "EXCHANGE" : "RETURN",
  );
  const [photos, setPhotos] = useState(() => initialPhotos(state.values?.fileIds));
  const [uploading, setUploading] = useState(false);
  const reasons = reasonsFor(type);

  return (
    <form action={formAction} className="grid gap-5">
      <input type="hidden" name="orderId" value={orderId} />
      <input type="hidden" name="sellerOrderId" value={sellerOrderId} />
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <input type="hidden" name="fileIds" value={JSON.stringify(photos)} />
      <FormAlert state={state} />

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">신청 종류</legend>
        {(Object.keys(TYPE_LABELS) as ClaimType[]).map((value) => (
          <label
            key={value}
            className="flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm has-[:checked]:border-primary"
          >
            <input
              type="radio"
              name="type"
              value={value}
              checked={type === value}
              onChange={() => setType(value)}
              className="mt-1"
            />
            <span className="grid gap-0.5">
              <span className="font-medium">{TYPE_LABELS[value].title}</span>
              <span className="text-muted-foreground">{TYPE_LABELS[value].description}</span>
            </span>
          </label>
        ))}
      </fieldset>

      <div className="grid gap-1.5">
        <Label htmlFor="reason">사유</Label>
        <select
          id="reason"
          name="reason"
          required
          key={type}
          defaultValue={
            state.values?.reason && reasons.some((reason) => reason === state.values?.reason)
              ? state.values.reason
              : ""
          }
          aria-invalid={errors.reason ? true : undefined}
          aria-describedby={errors.reason ? "reason-error" : "reason-hint"}
          className="h-10 w-full rounded-md border border-input bg-card px-3 text-sm"
        >
          <option value="" disabled>
            사유를 골라 주세요
          </option>
          {reasons.map((reason) => (
            <option key={reason} value={reason}>
              {CLAIM_REASON_LABELS[reason]}
            </option>
          ))}
        </select>
        {errors.reason ? (
          <p id="reason-error" className="text-xs text-destructive">
            {errors.reason}
          </p>
        ) : (
          <p id="reason-hint" className="text-xs text-muted-foreground">
            단순 변심 반품은 왕복 반품 배송비를 빼고 환불됩니다. 판매자 귀책이면 전액 환불됩니다.
          </p>
        )}
      </div>

      <TextAreaField
        name="description"
        label="자세한 내용"
        required
        maxLength={CLAIM_LIMITS.description}
        error={errors.description}
        defaultValue={state.values?.description}
        hint={`${CLAIM_LIMITS.description}자까지. 판매자와 관리자가 봅니다.`}
      />

      <PhotoUpload
        purpose="CLAIM_IMAGE"
        limit={CLAIM_LIMITS.photos}
        photos={photos}
        onPhotosChange={setPhotos}
        onUploadingChange={setUploading}
        previewSrc={(fileId) => `/api/claim-images/${fileId}/w300`}
        audience="판매자와 관리자만 봅니다."
        error={errors.fileIds}
      />

      <div>
        <SubmitButton disabled={uploading} pendingText="신청하는 중…">
          신청하기
        </SubmitButton>
      </div>
    </form>
  );
}
