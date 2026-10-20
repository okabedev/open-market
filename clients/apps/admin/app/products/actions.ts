"use server";

import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { reasonSchema, reviewResult } from "@/lib/review";

/**
 * 상품 제재(CAT-5, ADM-2). 사유가 꼭 있어야 하고 판매자에게 보이며 감사 로그에 남는다. 제재된 상품은 구매자에게 보이지 않고 판매자가 풀 수
 * 없다. 성공하면 같은 목록 조건으로 돌아간다.
 */
export async function ban(_: FormState, form: FormData): Promise<FormState> {
  const productId = z.uuid().safeParse(form.get("productId"));
  if (!productId.success) {
    return { status: "error", message: "상품을 찾을 수 없습니다." };
  }
  const values = { reason: String(form.get("reason") ?? "") };
  const parsed = reasonSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const catalog = await bff.serviceClient("catalog");
  const { error, response } = await catalog.POST("/v1/admin/products/{productId}/ban", {
    params: { path: { productId: productId.data } },
    body: parsed.data,
  });
  return reviewResult(error, response, form, "/products", "banned");
}
