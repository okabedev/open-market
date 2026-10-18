"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { z } from "zod";
import { bff } from "@/lib/bff";

const resendSchema = z.object({
  email: z.string().trim().pipe(z.email("이메일 형식이 아닙니다.")),
});

/** 인증 메일을 다시 보낸다. 가입 여부를 드러내지 않도록 auth는 항상 202다. */
export async function resendVerification(_: FormState, form: FormData): Promise<FormState> {
  const values = { email: String(form.get("email") ?? "") };
  const parsed = resendSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const auth = await bff.publicClient("auth");
  const { error, response } = await auth.POST("/v1/accounts/email-verification/resend", {
    body: parsed.data,
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  return { status: "success", message: "가입한 이메일이면 인증 메일을 다시 보냈습니다." };
}
