"use client";

import { FormAlert, idleState, SubmitButton, TextField } from "@openmarket/ui";
import { useActionState } from "react";
import { changeProfile } from "./actions";

export function ProfileForm({ name, phone }: { name: string; phone: string }) {
  const [state, action] = useActionState(changeProfile, idleState);
  return (
    <form action={action} className="grid gap-4" noValidate>
      <FormAlert state={state} />
      <TextField
        name="name"
        label="이름"
        autoComplete="name"
        required
        defaultValue={state.values?.name ?? name}
        error={state.fieldErrors?.name}
      />
      <TextField
        name="phone"
        label="연락처"
        type="tel"
        autoComplete="tel"
        placeholder="010-1234-5678"
        hint="비우면 연락처를 지웁니다."
        defaultValue={state.values?.phone ?? phone}
        error={state.fieldErrors?.phone}
      />
      <div>
        <SubmitButton>저장</SubmitButton>
      </div>
    </form>
  );
}
