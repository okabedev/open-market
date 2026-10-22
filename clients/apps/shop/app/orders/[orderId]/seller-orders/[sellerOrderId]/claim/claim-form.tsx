"use client";

import {
  Button,
  CLAIM_REASON_LABELS,
  FormAlert,
  idleState,
  Input,
  Label,
  SubmitButton,
  TextAreaField,
} from "@openmarket/ui";
import { useActionState, useState } from "react";
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
 * 반품·교환 신청 폼(CLM-2). 사진은 고르는 즉시 BFF(`/api/files`)로 올리고 파일 ID만 갖는다. 단순 변심 반품은 반품 배송비를 빼고
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
  const [uploadError, setUploadError] = useState<string | null>(null);
  const reasons = reasonsFor(type);

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) {
      return;
    }
    setUploadError(null);
    setUploading(true);
    let count = photos.length;
    try {
      for (const file of Array.from(files)) {
        if (count >= CLAIM_LIMITS.photos) {
          setUploadError(`사진은 ${CLAIM_LIMITS.photos}장까지입니다.`);
          break;
        }
        if (file.size > CLAIM_LIMITS.photoBytes) {
          setUploadError(`${file.name}: 이미지는 10MB까지 올릴 수 있습니다.`);
          continue;
        }
        const body = new FormData();
        body.set("file", file);
        const response = await fetch("/api/files", { method: "POST", body });
        const result = (await response.json().catch(() => ({}))) as {
          fileId?: string;
          detail?: string;
        };
        if (!response.ok || !result.fileId) {
          setUploadError(`${file.name}: ${result.detail ?? "사진을 올리지 못했습니다."}`);
          continue;
        }
        const fileId = result.fileId;
        count += 1;
        setPhotos((current) => [...current, fileId]);
      }
    } finally {
      setUploading(false);
    }
  }

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

      <div className="grid gap-2">
        <Label htmlFor="photos">사진(선택)</Label>
        {photos.length > 0 ? (
          <ul aria-label="올린 사진" className="flex flex-wrap gap-2">
            {photos.map((fileId, index) => (
              <li key={fileId} className="grid gap-1 text-xs">
                {/* eslint-disable-next-line @next/next/no-img-element -- 비공개 변형 JPEG을 BFF로 그대로 받는다. */}
                <img
                  src={`/api/claim-images/${fileId}/w300`}
                  alt={`올린 사진 ${index + 1}`}
                  width={96}
                  height={96}
                  className="size-24 rounded-md border bg-muted object-cover"
                />
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => setPhotos((current) => current.filter((id) => id !== fileId))}
                  aria-label={`사진 ${index + 1} 빼기`}
                >
                  빼기
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
        <Input
          id="photos"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          disabled={uploading || photos.length >= CLAIM_LIMITS.photos}
          aria-describedby="photos-hint"
          onChange={(event) => {
            void upload(event.currentTarget.files);
            event.currentTarget.value = "";
          }}
        />
        <p id="photos-hint" className="text-xs text-muted-foreground">
          JPEG, PNG, WebP {CLAIM_LIMITS.photos}장까지, 한 장에 10MB까지입니다. 판매자와 관리자만
          봅니다.
        </p>
        {uploading ? <p className="text-xs text-muted-foreground">올리는 중…</p> : null}
        {uploadError ? <p className="text-xs text-destructive">{uploadError}</p> : null}
        {errors.fileIds ? <p className="text-xs text-destructive">{errors.fileIds}</p> : null}
      </div>

      <div>
        <SubmitButton disabled={uploading} pendingText="신청하는 중…">
          신청하기
        </SubmitButton>
      </div>
    </form>
  );
}
