"use client";

import { FormAlert, idleState, Input, SubmitButton } from "@openmarket/ui";
import { useActionState } from "react";
import { adjustStock } from "../actions";

type StockFormProps = {
  productId: string;
  skuId: string;
  /** 화면을 그릴 때 만든 멱등성 키. 두 번 눌러도 한 번만 반영된다 */
  idempotencyKey: string;
  label: string;
};

/** SKU 하나의 재고 조정(CAT-3). 늘리면 양수, 줄이면 음수를 적는다. */
export function StockForm({ productId, skuId, idempotencyKey, label }: StockFormProps) {
  const [state, formAction] = useActionState(adjustStock, idleState);
  const errors = state.fieldErrors ?? {};
  return (
    <form action={formAction} className="grid gap-1">
      <input type="hidden" name="productId" value={productId} />
      <input type="hidden" name="skuId" value={skuId} />
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <div className="flex flex-wrap items-center gap-1">
        <Input
          name="delta"
          type="number"
          required
          min={-999_999}
          max={999_999}
          placeholder="+10 / -3"
          aria-label={`${label} 증감 수량`}
          aria-invalid={errors.delta ? true : undefined}
          defaultValue={state.values?.delta}
          className="w-24"
        />
        <Input
          name="reason"
          maxLength={100}
          placeholder="사유(선택)"
          aria-label={`${label} 조정 사유`}
          defaultValue={state.values?.reason}
          className="w-36"
        />
        <SubmitButton size="sm" variant="outline">
          조정
        </SubmitButton>
      </div>
      {errors.delta ? <p className="text-xs text-destructive">{errors.delta}</p> : null}
      {errors.reason ? <p className="text-xs text-destructive">{errors.reason}</p> : null}
      <FormAlert state={state} />
    </form>
  );
}
