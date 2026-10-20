"use client";

import { ConfirmSubmit } from "./confirm-submit";
import { FormAlert } from "./form-alert";
import { idleState, type FormState } from "./form-state";
import { useActionState, type ComponentProps } from "react";

type ConfirmActionFormProps = Omit<ComponentProps<typeof ConfirmSubmit>, "size"> & {
  action: (state: FormState, form: FormData) => Promise<FormState>;
  hidden: Record<string, string>;
};

/** 한 번 더 묻고 실행하는 작업(승인, 정지 해제, 판매 시작·중지). 실패하면 그 자리에 문구를 보여 준다. */
export function ConfirmActionForm({ action, hidden, ...confirm }: ConfirmActionFormProps) {
  const [state, formAction] = useActionState(action, idleState);
  return (
    <form action={formAction} className="grid gap-2">
      {Object.entries(hidden).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <FormAlert state={state} />
      <div>
        <ConfirmSubmit size="sm" {...confirm} />
      </div>
    </form>
  );
}
