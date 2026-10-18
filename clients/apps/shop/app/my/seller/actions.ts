"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { nameField, text } from "@/lib/contact";

/** member의 규칙(docs/architecture/member.md "입점과 판매자")과 같다. 사업자 번호 검증 숫자는 member가 확인한다. */
const applicationSchema = z.object({
  storeName: z.string().trim().min(2, "상호는 2자 이상입니다.").max(30, "상호는 30자까지입니다."),
  businessNumber: z
    .string()
    .trim()
    .regex(/^(\d{3}-\d{2}-\d{5}|\d{10})$/, "사업자 등록 번호는 123-45-67890 형식입니다."),
  representativeName: nameField,
  bankName: z
    .string()
    .trim()
    .min(1, "은행을 입력해 주세요.")
    .max(20, "은행 이름은 20자까지입니다."),
  accountHolder: nameField,
  accountNumber: z
    .string()
    .trim()
    .transform((value) => value.replace(/[\s-]/g, ""))
    .pipe(z.string().regex(/^\d{10,16}$/, "계좌 번호는 숫자 10~16자리입니다.")),
});

/** 입점 신청(SEL-1). 계좌 번호는 폼 값으로 되돌려 주지 않는다. */
export async function submitApplication(_: FormState, form: FormData): Promise<FormState> {
  const values = {
    storeName: text(form, "storeName"),
    businessNumber: text(form, "businessNumber"),
    representativeName: text(form, "representativeName"),
    bankName: text(form, "bankName"),
    accountHolder: text(form, "accountHolder"),
  };
  const parsed = applicationSchema.safeParse({
    ...values,
    accountNumber: text(form, "accountNumber"),
  });
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST("/v1/members/me/seller-applications", {
    body: parsed.data,
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  revalidatePath("/my/seller");
  return {
    status: "success",
    message: "입점을 신청했습니다. 심사 결과는 이 화면에서 확인할 수 있습니다.",
  };
}
