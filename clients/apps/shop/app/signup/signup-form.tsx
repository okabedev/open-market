"use client";

import { FormAlert, idleState, SubmitButton, TextField } from "@openmarket/ui";
import { useActionState } from "react";
import { signup } from "./actions";

export function SignupForm() {
  const [state, action] = useActionState(signup, idleState);
  return (
    <form action={action} className="grid gap-4" noValidate>
      <FormAlert state={state} />
      <TextField
        name="name"
        label="이름"
        autoComplete="name"
        required
        defaultValue={state.values?.name}
        error={state.fieldErrors?.name}
      />
      <TextField
        name="email"
        label="이메일"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
        error={state.fieldErrors?.email}
      />
      <TextField
        name="password"
        label="비밀번호"
        type="password"
        autoComplete="new-password"
        required
        hint="10자 이상, 글자와 숫자를 함께 넣어 주세요."
        error={state.fieldErrors?.password}
      />
      <SubmitButton>가입하기</SubmitButton>
    </form>
  );
}
