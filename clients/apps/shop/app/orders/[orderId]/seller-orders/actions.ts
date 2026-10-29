"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { CLAIM_LIMITS, CLAIM_REASONS, CLAIM_TYPES, reasonsFor } from "@/lib/claim";
import { text } from "@/lib/contact";
import { sellerOrderPath } from "@/lib/fulfillment-notice";

const uuid = z.uuid();

const INVALID_TARGET: FormState = { status: "error", message: "주문을 찾을 수 없습니다." };

/** 폼의 주문·하위 주문 ID. 화면이 넣은 값이지만 경로에 쓰므로 UUID인지 본다. */
function target(form: FormData): { orderId: string; sellerOrderId: string } | null {
  const orderId = uuid.safeParse(text(form, "orderId"));
  const sellerOrderId = uuid.safeParse(text(form, "sellerOrderId"));
  return orderId.success && sellerOrderId.success
    ? { orderId: orderId.data, sellerOrderId: sellerOrderId.data }
    : null;
}

/**
 * 발송 전 하위 주문을 취소한다(CLM-1). 신청과 함께 승인되고 환불된다. 같은 요청을 다시 보내도 결과가 같아 멱등성 키를 쓰지 않는다
 * (docs/architecture/fulfillment.md "발송 전 취소"). 그사이 발송됐으면 409 문구를 보여 준다.
 */
export async function cancelSellerOrder(_: FormState, form: FormData): Promise<FormState> {
  const ids = target(form);
  if (!ids) {
    return INVALID_TARGET;
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const { error, response } = await fulfillment.POST("/v1/seller-orders/{sellerOrderId}/cancel", {
    params: { path: { sellerOrderId: ids.sellerOrderId } },
  });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(sellerOrderPath(ids.orderId, ids.sellerOrderId, "cancelled"));
}

/** 구매를 확정한다(FUL-3). 이미 확정했으면 그대로 성공이다. 확정한 뒤에는 반품·교환을 신청할 수 없다. */
export async function confirmPurchase(_: FormState, form: FormData): Promise<FormState> {
  const ids = target(form);
  if (!ids) {
    return INVALID_TARGET;
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const { error, response } = await fulfillment.POST("/v1/seller-orders/{sellerOrderId}/confirm", {
    params: { path: { sellerOrderId: ids.sellerOrderId } },
  });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(sellerOrderPath(ids.orderId, ids.sellerOrderId, "confirmed"));
}

const claimType = z.enum(CLAIM_TYPES, { error: "반품인지 교환인지 골라 주세요." });
const claimReason = z.enum(CLAIM_REASONS, { error: "사유를 골라 주세요." });

/**
 * 유형과 사유의 조합 검사는 따로 둔 스키마와 교집합으로 묶는다. 한 객체의 `.refine`이면 사진 수 초과(zod 4.6.6부터 파싱을 멈추는
 * 오류)가 있을 때 건너뛰어, 고친 뒤 다시 보내야 사유 오류를 알게 된다.
 */
const claimSchema = z.intersection(
  z.object({
    type: claimType,
    reason: claimReason,
    description: z
      .string()
      .trim()
      .min(1, "사유를 자세히 적어 주세요.")
      .max(CLAIM_LIMITS.description, `설명은 ${CLAIM_LIMITS.description}자까지 쓸 수 있습니다.`),
    fileIds: z
      .array(z.uuid())
      .max(CLAIM_LIMITS.photos, `사진은 ${CLAIM_LIMITS.photos}장까지 올릴 수 있습니다.`),
  }),
  z
    .object({ type: claimType, reason: claimReason })
    .refine((claim) => reasonsFor(claim.type).includes(claim.reason), {
      path: ["reason"],
      message: "교환은 상품 불량, 오배송, 상품 정보와 다름일 때만 신청할 수 있습니다.",
    }),
);

function fileIds(value: string): unknown {
  try {
    return value ? JSON.parse(value) : [];
  } catch {
    return null;
  }
}

/**
 * 반품·교환을 신청한다(CLM-2). 사진은 미리 BFF(`/api/files`)로 올린 파일 ID다. 화면을 그릴 때 만든 멱등성 키를 보내 두 번 눌러도 한
 * 번만 신청된다. 실패하면 입력을 남긴다.
 */
export async function requestClaim(_: FormState, form: FormData): Promise<FormState> {
  const ids = target(form);
  if (!ids) {
    return INVALID_TARGET;
  }
  const values = {
    type: text(form, "type"),
    reason: text(form, "reason"),
    description: text(form, "description"),
    fileIds: text(form, "fileIds"),
  };
  const parsed = claimSchema.safeParse({ ...values, fileIds: fileIds(values.fileIds) });
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const { error, response } = await fulfillment.POST("/v1/seller-orders/{sellerOrderId}/claims", {
    params: { path: { sellerOrderId: ids.sellerOrderId } },
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
    body: parsed.data,
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  redirect(sellerOrderPath(ids.orderId, ids.sellerOrderId, "claim-requested"));
}

/**
 * 판매자가 거부한 반품·교환의 관리자 중재를 요청한다(CLM-3). 클레임마다 한 번, 거부 뒤 7일 안에만 된다. 이미 요청했으면 그대로 성공이다.
 */
export async function requestMediation(_: FormState, form: FormData): Promise<FormState> {
  const ids = target(form);
  const claimId = uuid.safeParse(text(form, "claimId"));
  if (!ids || !claimId.success) {
    return INVALID_TARGET;
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const { error, response } = await fulfillment.POST("/v1/claims/{claimId}/mediation", {
    params: { path: { claimId: claimId.data } },
  });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(sellerOrderPath(ids.orderId, ids.sellerOrderId, "mediation-requested"));
}
