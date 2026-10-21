"use client";

import { idleState, Input, SubmitButton, type FormState } from "@openmarket/ui";
import { useActionState } from "react";

type QuantityFormProps = {
  action: (state: FormState, form: FormData) => Promise<FormState>;
  /** 숨은 값(SKU ID, 상품 ID, 멱등성 키) */
  hidden: Record<string, string>;
  /** 한 화면에 여럿 있으므로 입력 id를 따로 준다 */
  id: string;
  /** 화면에서 보이지 않는 이름표(예: "반팔 티셔츠 M 수량") */
  label: string;
  defaultQuantity: number;
  submitText: string;
};

/** 수량 입력과 제출 버튼 한 줄. 상품 상세의 담기와 장바구니의 수량 바꾸기가 쓴다. 실패하면 문구를 아래에 보여 준다. */
export function QuantityForm({
  action,
  hidden,
  id,
  label,
  defaultQuantity,
  submitText,
}: QuantityFormProps) {
  const [state, formAction] = useActionState(action, idleState);
  const error = state.status === "error" ? (state.fieldErrors?.quantity ?? state.message) : null;
  return (
    <form action={formAction} className="grid justify-items-end gap-1" noValidate>
      {Object.entries(hidden).map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
      <div className="flex items-center gap-2">
        <label htmlFor={id} className="sr-only">
          {label}
        </label>
        <Input
          id={id}
          name="quantity"
          type="number"
          inputMode="numeric"
          min={1}
          max={99}
          defaultValue={state.values?.quantity ?? String(defaultQuantity)}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className="w-20"
        />
        <SubmitButton size="sm" variant="outline">
          {submitText}
        </SubmitButton>
      </div>
      {error ? (
        <p id={`${id}-error`} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </form>
  );
}
