"use server";

import { problemState, type FormState } from "@openmarket/ui";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { bff } from "@/lib/bff";
import { text } from "@/lib/contact";

/**
 * 주문을 만든다(ORD-2). 줄(SKU, 수량, 기대 가격)은 주문서가 숨은 값으로 보낸다. 배송지는 ID만 받고 member에서 읽어 넣는다. 주문서를 그릴
 * 때 만든 멱등성 키를 보내 두 번 눌러도 주문은 하나다. 만들면 결제 화면으로 간다. 거절되면(가격 변경, 재고 부족 등) 주문서를 새로 그린다.
 * 새 가격과 새 키가 나온다.
 */
export async function placeOrder(_: FormState, form: FormData): Promise<FormState> {
  const addressId = text(form, "addressId");
  if (!addressId) {
    return { status: "error", fieldErrors: { addressId: "배송지를 골라 주세요." } };
  }
  const member = await bff.serviceClient("member");
  const { data: addresses } = await member.GET("/v1/members/me/addresses");
  const address = addresses?.find((candidate) => candidate.addressId === addressId);
  if (!address) {
    return {
      status: "error",
      fieldErrors: { addressId: "배송지를 찾지 못했습니다. 다시 골라 주세요." },
    };
  }
  const skuIds = form.getAll("skuId").map(String);
  const quantities = form.getAll("quantity").map(Number);
  const prices = form.getAll("expectedPrice").map(Number);
  const lines = skuIds.map((skuId, index) => ({
    skuId,
    quantity: quantities[index] ?? 0,
    expectedPrice: prices[index] ?? 0,
  }));
  const order = await bff.serviceClient("order");
  const { data, error, response } = await order.POST("/v1/orders", {
    body: {
      lines,
      shipping: {
        recipientName: address.recipientName ?? "",
        recipientPhone: address.recipientPhone ?? "",
        zipCode: address.zipCode ?? "",
        baseAddress: address.baseAddress ?? "",
        ...(address.detailAddress ? { detailAddress: address.detailAddress } : {}),
      },
    },
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
  });
  if (!response.ok || !data?.orderId) {
    revalidatePath("/checkout");
    return problemState(error, response.status);
  }
  redirect(`/orders/${data.orderId}/pay`);
}
