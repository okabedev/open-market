"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { bff } from "@/lib/bff";
import { signupSchema } from "./schema";

/** 가입(ACC-1). auth 가입 API는 공개 경로라 토큰 없이 부른다. 브라우저 주소를 넘겨 IP별 한도를 받는다(NFR-5). */
export async function signup(_: FormState, form: FormData): Promise<FormState> {
  const values = { name: String(form.get("name") ?? ""), email: String(form.get("email") ?? "") };
  const parsed = signupSchema.safeParse({
    ...values,
    password: String(form.get("password") ?? ""),
  });
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const auth = await bff.publicClient("auth");
  const { error, response } = await auth.POST("/v1/accounts", { body: parsed.data });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  redirect("/signup/done");
}
