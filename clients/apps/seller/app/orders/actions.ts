"use server";

import { CARRIER_LABELS, fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { orderPath, type OrderNotice } from "@/lib/order-notice";

const uuid = z.uuid();

function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}

const INVALID_TARGET: FormState = { status: "error", message: "주문을 찾을 수 없습니다." };

/** 폼의 하위 주문·클레임 ID. 화면이 넣은 값이지만 경로에 쓰므로 UUID인지 본다. */
function ids(form: FormData): { sellerOrderId: string; claimId: string | null } | null {
  const sellerOrderId = uuid.safeParse(text(form, "sellerOrderId"));
  if (!sellerOrderId.success) {
    return null;
  }
  const claimId = uuid.safeParse(text(form, "claimId"));
  return { sellerOrderId: sellerOrderId.data, claimId: claimId.success ? claimId.data : null };
}

/**
 * fulfillment 응답을 처리한다. 성공하면 상세로 돌아가 결과를 알린다. 409(그사이 상태가 바뀜)면 화면이 지금 상태를 다시 읽도록 상세로
 * 돌아간다(docs/architecture/fulfillment.md "발주 확인과 발송").
 */
function settle(
  sellerOrderId: string,
  notice: OrderNotice,
  { error, response }: { error?: unknown; response: Response },
  values?: Record<string, string>,
): FormState {
  if (response.status === 409) {
    redirect(orderPath(sellerOrderId, "conflict"));
  }
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  redirect(orderPath(sellerOrderId, notice));
}

/** 발주를 확인한다(FUL-1). 같은 동작을 다시 해도 결과가 같아 멱등성 키를 보내지 않는다. */
export async function acceptOrder(_: FormState, form: FormData): Promise<FormState> {
  const target = ids(form);
  if (!target) {
    return INVALID_TARGET;
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const result = await fulfillment.POST("/v1/seller/seller-orders/{sellerOrderId}/accept", {
    params: { path: { sellerOrderId: target.sellerOrderId } },
  });
  return settle(target.sellerOrderId, "accepted", result);
}

const carriers = Object.keys(CARRIER_LABELS) as [keyof typeof CARRIER_LABELS];

const shipSchema = z.object({
  carrier: z.enum(carriers, { error: "택배사를 골라 주세요." }),
  trackingNumber: z
    .string()
    .transform((value) => value.replace(/[\s-]/g, ""))
    .pipe(z.string().regex(/^\d{10,14}$/, "송장 번호는 숫자 10~14자리입니다.")),
});

/** 송장을 넣어 발송한다(FUL-1). 송장 번호의 공백과 하이픈은 빼고 보낸다. */
export async function shipOrder(_: FormState, form: FormData): Promise<FormState> {
  const target = ids(form);
  if (!target) {
    return INVALID_TARGET;
  }
  const values = { carrier: text(form, "carrier"), trackingNumber: text(form, "trackingNumber") };
  const parsed = shipSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const result = await fulfillment.POST("/v1/seller/seller-orders/{sellerOrderId}/ship", {
    params: { path: { sellerOrderId: target.sellerOrderId } },
    body: parsed.data,
  });
  return settle(target.sellerOrderId, "shipped", result, values);
}

/**
 * 반품·교환을 승인한다(CLM-3). 반품은 재입고 여부를 꼭 고른다. 교환은 같은 상품을 다시 보낼 배송이 생긴다
 * (docs/architecture/fulfillment.md "판매자 승인과 거부").
 */
export async function approveClaim(_: FormState, form: FormData): Promise<FormState> {
  const target = ids(form);
  if (!target?.claimId) {
    return INVALID_TARGET;
  }
  const restock = text(form, "restock");
  if (text(form, "type") === "RETURN" && restock !== "true" && restock !== "false") {
    return { status: "error", fieldErrors: { restock: "재입고할지 골라 주세요." } };
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const result = await fulfillment.POST("/v1/seller/claims/{claimId}/approve", {
    params: { path: { claimId: target.claimId } },
    body: restock ? { restock: restock === "true" } : {},
  });
  return settle(target.sellerOrderId, "claim-approved", result);
}

const rejectSchema = z.object({
  reason: z
    .string()
    .trim()
    .min(1, "거부 사유를 입력해 주세요.")
    .max(500, "거부 사유는 500자까지 쓸 수 있습니다."),
});

/** 반품·교환을 거부한다(CLM-3). 사유는 구매자에게 보이고, 구매자는 관리자 중재를 요청할 수 있다. */
export async function rejectClaim(_: FormState, form: FormData): Promise<FormState> {
  const target = ids(form);
  if (!target?.claimId) {
    return INVALID_TARGET;
  }
  const values = { reason: text(form, "reason") };
  const parsed = rejectSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const result = await fulfillment.POST("/v1/seller/claims/{claimId}/reject", {
    params: { path: { claimId: target.claimId } },
    body: parsed.data,
  });
  return settle(target.sellerOrderId, "claim-rejected", result, values);
}
