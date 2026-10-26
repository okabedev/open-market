"use client";

import { FormAlert, idleState, Input, SubmitButton } from "@openmarket/ui";
import { useActionState } from "react";
import { setCommissionRate } from "./actions";

/**
 * 카테고리 하나의 수수료율 입력(%). 목록 행마다 하나라 입력 id를 카테고리 ID로 만든다. 정한 요율이 있으면 그 값을, 없으면 비워 기본
 * 요율을 자리 표시로 보인다.
 */
export function RateForm({
  categoryId,
  categoryName,
  current,
  placeholder,
}: {
  categoryId: string;
  categoryName: string;
  /** 정한 요율(%). 없으면 기본 요율을 쓴다 */
  current: string | undefined;
  placeholder: string;
}) {
  const [state, formAction] = useActionState(setCommissionRate, idleState);
  const id = `rate-${categoryId}`;
  const error = state.fieldErrors?.rate;
  return (
    <form action={formAction} className="grid gap-1">
      <input type="hidden" name="categoryId" value={categoryId} />
      <FormAlert state={state.fieldErrors ? idleState : state} />
      <div className="flex items-center gap-2">
        <label htmlFor={id} className="sr-only">
          {categoryName} 수수료율(%)
        </label>
        <Input
          id={id}
          name="rate"
          inputMode="decimal"
          className="w-24"
          defaultValue={state.values?.rate ?? current ?? ""}
          placeholder={placeholder}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
        />
        <span className="text-sm text-muted-foreground">%</span>
        <SubmitButton size="sm" variant="outline" pendingText="저장 중…">
          저장
        </SubmitButton>
      </div>
      {error ? (
        <p id={`${id}-error`} className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </form>
  );
}
