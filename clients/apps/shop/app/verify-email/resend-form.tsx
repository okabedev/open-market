"use client";

import { FormAlert, idleState, SubmitButton, TextField } from "@openmarket/ui";
import { useActionState } from "react";
import { resendVerification } from "./actions";

export function ResendForm() {
  const [state, action] = useActionState(resendVerification, idleState);
  return (
    <form action={action} className="grid gap-4" noValidate>
      <FormAlert state={state} />
      <TextField
        name="email"
        label="가입한 이메일"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
        error={state.fieldErrors?.email}
      />
      <SubmitButton variant="outline">인증 메일 다시 받기</SubmitButton>
    </form>
  );
}
