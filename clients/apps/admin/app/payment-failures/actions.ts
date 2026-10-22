"use server";

import type { FormState } from "@openmarket/ui";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { reviewResult } from "@/lib/review";

const uuid = z.uuid();

/**
 * 실패한 환불을 다시 시도한다(PAY-2, docs/architecture/payment.md "관리자 재시도"). payment가 환불할 수 있는지 다시 확인하고 새
 * 멱등성 키로 토스를 부른다. 이미 재시도 중이면 그대로 성공이다. 감사 로그에 남는다.
 */
export async function retryRefund(_: FormState, form: FormData): Promise<FormState> {
  const refundId = uuid.safeParse(form.get("refundId"));
  if (!refundId.success) {
    return { status: "error", message: "환불을 찾을 수 없습니다." };
  }
  const payment = await bff.serviceClient("payment");
  const { error, response } = await payment.POST("/v1/admin/refunds/{refundId}/retry", {
    params: { path: { refundId: refundId.data } },
  });
  return reviewResult(error, response, form, "/payment-failures", "refund-retried");
}

/** 토스가 거절한 보상 취소(결제 기한이 지난 주문의 자동 취소)를 다시 시도한다. 취소 작업이 다음 주기에 부른다. */
export async function retryCancel(_: FormState, form: FormData): Promise<FormState> {
  const paymentId = uuid.safeParse(form.get("paymentId"));
  if (!paymentId.success) {
    return { status: "error", message: "결제를 찾을 수 없습니다." };
  }
  const payment = await bff.serviceClient("payment");
  const { error, response } = await payment.POST("/v1/admin/payments/{paymentId}/cancel-retry", {
    params: { path: { paymentId: paymentId.data } },
  });
  return reviewResult(error, response, form, "/payment-failures", "cancel-retried");
}
