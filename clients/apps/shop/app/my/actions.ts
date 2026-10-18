"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { revalidatePath } from "next/cache";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { nameField, phoneField, text } from "@/lib/contact";

const profileSchema = z.object({ name: nameField, phone: phoneField.or(z.literal("")) });

/** 이름과 연락처를 바꾼다(ACC-3). 연락처를 비우면 지운다. */
export async function changeProfile(_: FormState, form: FormData): Promise<FormState> {
  const values = { name: text(form, "name"), phone: text(form, "phone") };
  const parsed = profileSchema.safeParse(values);
  if (!parsed.success) {
    return { status: "error", fieldErrors: fieldErrorsFrom(parsed.error.issues), values };
  }
  const member = await bff.serviceClient("member");
  const { error, response } = await member.PUT("/v1/members/me", {
    body: { name: parsed.data.name, ...(parsed.data.phone ? { phone: parsed.data.phone } : {}) },
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  revalidatePath("/my");
  return { status: "success", message: "저장했습니다.", values };
}
