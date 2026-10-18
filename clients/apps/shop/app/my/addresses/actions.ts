"use server";

import { fieldErrorsFrom, problemState, type FormState } from "@openmarket/ui";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { bff } from "@/lib/bff";
import { nameField, phoneField, text } from "@/lib/contact";

const addressSchema = z.object({
  label: z.string().trim().max(20, "이름표는 20자까지입니다."),
  recipientName: nameField,
  recipientPhone: phoneField,
  zipCode: z
    .string()
    .trim()
    .regex(/^\d{5}$/, "우편번호는 숫자 5자리입니다."),
  baseAddress: z
    .string()
    .trim()
    .min(1, "기본 주소를 입력해 주세요.")
    .max(200, "기본 주소는 200자까지입니다."),
  detailAddress: z.string().trim().max(200, "상세 주소는 200자까지입니다."),
});

type AddressBody = {
  label?: string;
  recipientName: string;
  recipientPhone: string;
  zipCode: string;
  baseAddress: string;
  detailAddress?: string;
};

function read(form: FormData) {
  const values = {
    label: text(form, "label"),
    recipientName: text(form, "recipientName"),
    recipientPhone: text(form, "recipientPhone"),
    zipCode: text(form, "zipCode"),
    baseAddress: text(form, "baseAddress"),
    detailAddress: text(form, "detailAddress"),
  };
  const parsed = addressSchema.safeParse(values);
  if (!parsed.success) {
    return {
      values,
      failed: {
        status: "error",
        fieldErrors: fieldErrorsFrom(parsed.error.issues),
        values,
      } as FormState,
    };
  }
  const { label, detailAddress, ...required } = parsed.data;
  const body: AddressBody = {
    ...required,
    ...(label ? { label } : {}),
    ...(detailAddress ? { detailAddress } : {}),
  };
  return { values, body };
}

/**
 * 배송지를 더한다. 폼을 그릴 때 만든 멱등성 키(`idempotencyKey`)를 보내서, 두 번 눌러도 하나만 생긴다.
 * 10개를 넘으면 member가 422를 준다.
 */
export async function addAddress(_: FormState, form: FormData): Promise<FormState> {
  const { values, body, failed } = read(form);
  if (!body) {
    return failed;
  }
  const member = await bff.serviceClient("member");
  const { error, response } = await member.POST("/v1/members/me/addresses", {
    body: { ...body, asDefault: form.get("asDefault") === "on" },
    headers: { "Idempotency-Key": text(form, "idempotencyKey") },
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  revalidatePath("/my/addresses");
  redirect("/my/addresses");
}

export async function changeAddress(_: FormState, form: FormData): Promise<FormState> {
  const { values, body, failed } = read(form);
  if (!body) {
    return failed;
  }
  const member = await bff.serviceClient("member");
  const { error, response } = await member.PUT("/v1/members/me/addresses/{addressId}", {
    params: { path: { addressId: text(form, "addressId") } },
    body,
  });
  if (!response.ok) {
    return problemState(error, response.status, values);
  }
  revalidatePath("/my/addresses");
  redirect("/my/addresses");
}

export async function setDefaultAddress(form: FormData): Promise<void> {
  const member = await bff.serviceClient("member");
  await member.POST("/v1/members/me/addresses/{addressId}/default", {
    params: { path: { addressId: text(form, "addressId") } },
  });
  revalidatePath("/my/addresses");
}

export async function removeAddress(form: FormData): Promise<void> {
  const member = await bff.serviceClient("member");
  await member.DELETE("/v1/members/me/addresses/{addressId}", {
    params: { path: { addressId: text(form, "addressId") } },
  });
  revalidatePath("/my/addresses");
}
