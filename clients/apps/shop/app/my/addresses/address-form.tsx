"use client";

import { FormAlert, idleState, SubmitButton, TextField, type FormState } from "@openmarket/ui";
import { useActionState } from "react";

export type AddressValues = {
  label: string;
  recipientName: string;
  recipientPhone: string;
  zipCode: string;
  baseAddress: string;
  detailAddress: string;
};

type AddressFormProps = {
  action: (state: FormState, form: FormData) => Promise<FormState>;
  initial: AddressValues;
  /** 숨은 값. 추가할 때는 멱등성 키, 고칠 때는 배송지 ID */
  hidden: Record<string, string>;
  /** 추가할 때만 기본 배송지 지정을 묻는다 */
  askDefault?: boolean;
  submitText: string;
};

export function AddressForm({
  action,
  initial,
  hidden,
  askDefault = false,
  submitText,
}: AddressFormProps) {
  const [state, formAction] = useActionState(action, idleState);
  const value = (name: keyof AddressValues) => state.values?.[name] ?? initial[name];
  const error = (name: keyof AddressValues) => state.fieldErrors?.[name];
  return (
    <form action={formAction} className="grid gap-4" noValidate>
      {Object.entries(hidden).map(([name, hiddenValue]) => (
        <input key={name} type="hidden" name={name} value={hiddenValue} />
      ))}
      <FormAlert state={state} />
      <TextField
        name="label"
        label="이름표"
        placeholder="집, 회사"
        defaultValue={value("label")}
        error={error("label")}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          name="recipientName"
          label="받는 사람"
          autoComplete="name"
          required
          defaultValue={value("recipientName")}
          error={error("recipientName")}
        />
        <TextField
          name="recipientPhone"
          label="받는 사람 연락처"
          type="tel"
          autoComplete="tel"
          placeholder="010-1234-5678"
          required
          defaultValue={value("recipientPhone")}
          error={error("recipientPhone")}
        />
      </div>
      <TextField
        name="zipCode"
        label="우편번호"
        inputMode="numeric"
        autoComplete="postal-code"
        className="sm:max-w-40"
        required
        defaultValue={value("zipCode")}
        error={error("zipCode")}
      />
      <TextField
        name="baseAddress"
        label="기본 주소"
        autoComplete="address-line1"
        required
        defaultValue={value("baseAddress")}
        error={error("baseAddress")}
      />
      <TextField
        name="detailAddress"
        label="상세 주소"
        autoComplete="address-line2"
        defaultValue={value("detailAddress")}
        error={error("detailAddress")}
      />
      {askDefault ? (
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="asDefault" className="size-4 accent-primary" />
          기본 배송지로 지정
        </label>
      ) : null}
      <div>
        <SubmitButton>{submitText}</SubmitButton>
      </div>
    </form>
  );
}
