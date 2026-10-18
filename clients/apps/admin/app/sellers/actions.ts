"use server";

import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { reasonSchema, reviewResult } from "@/lib/review";

function id(form: FormData): string {
  const value = form.get("sellerId");
  return typeof value === "string" ? value : "";
}

/** 판매자 정지(SEL-3). 사유가 꼭 있어야 한다. 판매자 역할은 다음 토큰 갱신부터 빠진다. */
export async function suspend(_: FormState, form: FormData): Promise<FormState> {
  const values = { reason: String(form.get("reason") ?? "") };
  const parsed = reasonSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST("/v1/admin/sellers/{sellerId}/suspend", {
    params: { path: { sellerId: id(form) } },
    body: parsed.data,
  });
  return reviewResult(error, response, form, "/sellers", "suspended");
}

export async function reinstate(_: FormState, form: FormData): Promise<FormState> {
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST("/v1/admin/sellers/{sellerId}/reinstate", {
    params: { path: { sellerId: id(form) } },
  });
  return reviewResult(error, response, form, "/sellers", "reinstated");
}
