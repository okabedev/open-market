"use client";

import { FormAlert, idleState, SubmitButton, TextAreaField, type FormState } from "@openmarket/ui";
import { useActionState, useId } from "react";

type ReasonFormProps = {
  action: (state: FormState, form: FormData) => Promise<FormState>;
  /** 대상 ID 같은 숨은 값 */
  hidden: Record<string, string>;
  label: string;
  submitText: string;
};

/** 사유가 꼭 있어야 하는 관리자 작업(반려, 정지, 제재). 사유는 감사 로그와 이벤트에 남는다. */
export function ReasonForm({ action, hidden, label, submitText }: ReasonFormProps) {
  const [state, formAction] = useActionState(action, idleState);
  // 목록 행마다 폼이 있으므로 입력 id를 행마다 다르게 한다(이름표가 자기 입력을 가리키게).
  const id = useId();
  return (
    <form action={formAction} className="grid gap-3" noValidate>
      {Object.entries(hidden).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <FormAlert state={state} />
      <TextAreaField
        id={`${id}-reason`}
        name="reason"
        label={label}
        required
        maxLength={500}
        hint="개인정보는 적지 않습니다. 감사 로그에 남습니다."
        defaultValue={state.values?.reason}
        error={state.fieldErrors?.reason}
      />
      <div>
        <SubmitButton variant="destructive" size="sm">
          {submitText}
        </SubmitButton>
      </div>
    </form>
  );
}
