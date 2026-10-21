"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { text } from "@/lib/contact";

/** 수량 규칙(docs/architecture/order.md "장바구니"). 최종 판단은 order가 한다(더한 수량 99 상한 등). */
const quantityField = z.coerce
  .number({ error: "수량은 1~99 사이 숫자입니다." })
  .int("수량은 1~99 사이 숫자입니다.")
  .min(1, "수량은 1~99 사이 숫자입니다.")
  .max(99, "수량은 1~99 사이 숫자입니다.");

function quantity(form: FormData): number | FormState {
  const raw = text(form, "quantity");
  const parsed = quantityField.safeParse(raw.trim() === "" ? Number.NaN : raw);
  if (!parsed.success) {
    return {
      status: "error",
      fieldErrors: fieldErrorsFrom(
        parsed.error.issues.map((issue) => ({ ...issue, path: ["quantity"] })),
      ),
      values: { quantity: raw },
    };
  }
  return parsed.data;
}

/**
 * SKU를 장바구니에 담는다. 같은 SKU면 order가 수량을 더하므로, 화면을 그릴 때 만든 멱등성 키(`idempotencyKey`)를 보내 두 번 눌러도
 * 한 번만 담긴다. 담으면 상품 화면으로 돌아가 안내한다(새 화면이 새 키를 만든다).
 */
export async function addToCart(_: FormState, form: FormData): Promise<FormState> {
  const count = quantity(form);
  if (typeof count !== "number") {
    return count;
  }
  const skuId = text(form, "skuId");
  const order = await bff.serviceClient("order");
  const { error, response } = await order.POST("/v1/cart/items", {
    body: { skuId, quantity: count },
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
  });
  if (!response.ok) {
    return problemState(error, response.status, { quantity: String(count) });
  }
  revalidatePath("/cart");
  redirect(`/products/${text(form, "productId")}?added=${skuId}`);
}

/** 담은 SKU의 수량을 바꾼다. 다시 보내도 결과가 같아 멱등성 키는 쓰지 않는다. */
export async function changeCartItemQuantity(_: FormState, form: FormData): Promise<FormState> {
  const count = quantity(form);
  if (typeof count !== "number") {
    return count;
  }
  const order = await bff.serviceClient("order");
  const { error, response } = await order.PUT("/v1/cart/items/{skuId}", {
    params: { path: { skuId: text(form, "skuId") } },
    body: { quantity: count },
  });
  if (!response.ok) {
    return problemState(error, response.status, { quantity: String(count) });
  }
  revalidatePath("/cart");
  return { status: "success" };
}

/** SKU를 장바구니에서 뺀다. 담지 않은 SKU여도 order는 204다. */
export async function removeCartItem(form: FormData): Promise<void> {
  const order = await bff.serviceClient("order");
  await order.DELETE("/v1/cart/items/{skuId}", {
    params: { path: { skuId: text(form, "skuId") } },
  });
  revalidatePath("/cart");
}
