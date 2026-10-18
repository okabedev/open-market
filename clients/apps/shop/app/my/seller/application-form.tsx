"use client";

import { FormAlert, idleState, SubmitButton, TextField } from "@openmarket/ui";
import { useActionState } from "react";
import { submitApplication } from "./actions";

export function ApplicationForm({ idempotencyKey }: { idempotencyKey: string }) {
  const [state, action] = useActionState(submitApplication, idleState);
  if (state.status === "success") {
    return <FormAlert state={state} />;
  }
  const value = (name: string) => state.values?.[name];
  const error = (name: string) => state.fieldErrors?.[name];
  return (
    <form action={action} className="grid gap-4" noValidate>
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <FormAlert state={state} />
      <fieldset className="grid gap-4">
        <legend className="mb-2 text-sm font-semibold">사업자 정보</legend>
        <TextField
          name="storeName"
          label="상호"
          required
          defaultValue={value("storeName")}
          error={error("storeName")}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            name="businessNumber"
            label="사업자 등록 번호"
            placeholder="123-45-67890"
            inputMode="numeric"
            required
            defaultValue={value("businessNumber")}
            error={error("businessNumber")}
          />
          <TextField
            name="representativeName"
            label="대표자"
            required
            defaultValue={value("representativeName")}
            error={error("representativeName")}
          />
        </div>
      </fieldset>
      <fieldset className="grid gap-4">
        <legend className="mb-2 text-sm font-semibold">정산 계좌</legend>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            name="bankName"
            label="은행"
            required
            defaultValue={value("bankName")}
            error={error("bankName")}
          />
          <TextField
            name="accountHolder"
            label="예금주"
            required
            defaultValue={value("accountHolder")}
            error={error("accountHolder")}
          />
        </div>
        <TextField
          name="accountNumber"
          label="계좌 번호"
          inputMode="numeric"
          autoComplete="off"
          required
          hint="암호화해 저장하고, 화면에는 끝 네 자리만 보입니다."
          error={error("accountNumber")}
        />
      </fieldset>
      <div>
        <SubmitButton>입점 신청</SubmitButton>
      </div>
    </form>
  );
}
