import { Alert } from "./alert";
import type { FormState } from "./form-state";

/** 폼 결과 문구. 성공·실패 문구가 없으면 아무것도 그리지 않는다. */
export function FormAlert({ state }: { state: FormState }) {
  if (state.status === "idle" || !state.message) {
    return null;
  }
  return (
    <Alert variant={state.status === "error" ? "destructive" : "success"}>{state.message}</Alert>
  );
}
