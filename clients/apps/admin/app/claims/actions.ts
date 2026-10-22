"use server";

import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { reasonSchema, reviewResult } from "@/lib/review";

const INVALID_TARGET: FormState = { status: "error", message: "클레임을 찾을 수 없습니다." };

function claimId(form: FormData): string | null {
  const parsed = z.uuid().safeParse(form.get("claimId"));
  return parsed.success ? parsed.data : null;
}

/**
 * 중재 승인(CLM-3, ADM-2). 관리자가 귀책을 고른다. 반품은 재입고하지 않고 고른 귀책으로 환불 금액을 정한다. 교환은 판매자 귀책이어야
 * 하고 새 배송을 만든다(docs/architecture/fulfillment.md "중재"). 감사 로그에 남는다.
 */
export async function approveMediation(_: FormState, form: FormData): Promise<FormState> {
  const id = claimId(form);
  if (!id) {
    return INVALID_TARGET;
  }
  const fault = form.get("fault");
  if (fault !== "BUYER" && fault !== "SELLER") {
    return { status: "error", fieldErrors: { fault: "귀책을 골라 주세요." } };
  }
  if (form.get("type") === "EXCHANGE" && fault !== "SELLER") {
    return {
      status: "error",
      fieldErrors: { fault: "교환은 판매자 귀책일 때만 승인할 수 있습니다." },
    };
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const { error, response } = await fulfillment.POST("/v1/admin/claims/{claimId}/approve", {
    params: { path: { claimId: id } },
    body: { fault },
  });
  return reviewResult(error, response, form, "/claims", "mediation-approved");
}

/** 중재 거부 확정. 사유는 구매자에게 보이고 감사 로그에 남는다. 다시 중재를 요청할 수 없다. */
export async function rejectMediation(_: FormState, form: FormData): Promise<FormState> {
  const id = claimId(form);
  if (!id) {
    return INVALID_TARGET;
  }
  const values = { reason: String(form.get("reason") ?? "") };
  const parsed = reasonSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const fulfillment = await bff.serviceClient("fulfillment");
  const { error, response } = await fulfillment.POST("/v1/admin/claims/{claimId}/reject", {
    params: { path: { claimId: id } },
    body: parsed.data,
  });
  return reviewResult(error, response, form, "/claims", "mediation-rejected");
}
