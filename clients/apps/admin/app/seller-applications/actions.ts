"use server";

import { fieldErrorsFrom, type FormState } from "@openmarket/ui";
import { bff } from "@/lib/bff";
import { reasonSchema, reviewResult } from "@/lib/review";

function id(form: FormData): string {
  const value = form.get("applicationId");
  return typeof value === "string" ? value : "";
}

/** 입점 승인(SEL-2). 판매자 역할은 신청자의 다음 토큰 갱신부터 붙는다. */
export async function approve(_: FormState, form: FormData): Promise<FormState> {
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST(
    "/v1/admin/seller-applications/{applicationId}/approve",
    {
      params: { path: { applicationId: id(form) } },
    },
  );
  return reviewResult(error, response, form, "/seller-applications", "approved");
}

/** 입점 반려(SEL-2). 사유가 꼭 있어야 한다. */
export async function reject(_: FormState, form: FormData): Promise<FormState> {
  const values = { reason: String(form.get("reason") ?? "") };
  const parsed = reasonSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST(
    "/v1/admin/seller-applications/{applicationId}/reject",
    {
      params: { path: { applicationId: id(form) } },
      body: parsed.data,
    },
  );
  return reviewResult(error, response, form, "/seller-applications", "rejected");
}
