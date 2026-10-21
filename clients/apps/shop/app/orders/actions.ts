"use server";

import { problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";

const uuid = z.uuid();

/**
 * 결제 전 주문을 취소한다(order.md "구매자 취소"). 재고 예약은 order가 낸 취소 이벤트로 풀린다. 성공하면 같은 주문 상세로 돌아가
 * 취소 사유를 보여 준다. 그 사이 결제가 끝났으면 order가 409로 거절한다.
 */
export async function cancelOrder(_: FormState, form: FormData): Promise<FormState> {
  const parsed = uuid.safeParse(form.get("orderId"));
  if (!parsed.success) {
    return { status: "error", message: "주문을 찾을 수 없습니다." };
  }
  const order = await bff.serviceClient("order");
  const { error, response } = await order.POST("/v1/orders/{orderId}/cancel", {
    params: { path: { orderId: parsed.data } },
  });
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(`/orders/${parsed.data}`);
}
