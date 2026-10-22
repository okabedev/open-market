"use client";

import {
  CARRIER_LABELS,
  FormAlert,
  idleState,
  Label,
  SubmitButton,
  TextField,
} from "@openmarket/ui";
import { useActionState } from "react";
import { shipOrder } from "../actions";

/** 송장 입력과 발송(FUL-1). 발주 확인한 배송에만 보인다. 택배사와 연동하지 않아 형식만 본다. */
export function ShipForm({ sellerOrderId }: { sellerOrderId: string }) {
  const [state, formAction] = useActionState(shipOrder, idleState);
  const errors = state.fieldErrors ?? {};
  return (
    <form action={formAction} className="grid gap-3">
      <input type="hidden" name="sellerOrderId" value={sellerOrderId} />
      <FormAlert state={state} />
      <div className="grid gap-3 sm:grid-cols-[12rem_1fr_auto] sm:items-start">
        <div className="grid gap-1.5">
          <Label htmlFor="carrier">택배사</Label>
          <select
            id="carrier"
            name="carrier"
            required
            defaultValue={state.values?.carrier ?? ""}
            aria-invalid={errors.carrier ? true : undefined}
            aria-describedby={errors.carrier ? "carrier-error" : undefined}
            className="h-10 w-full rounded-md border border-input bg-card px-3 text-sm"
          >
            <option value="" disabled>
              택배사를 골라 주세요
            </option>
            {Object.entries(CARRIER_LABELS).map(([code, name]) => (
              <option key={code} value={code}>
                {name}
              </option>
            ))}
          </select>
          {errors.carrier ? (
            <p id="carrier-error" className="text-xs text-destructive">
              {errors.carrier}
            </p>
          ) : null}
        </div>
        <TextField
          name="trackingNumber"
          label="송장 번호"
          inputMode="numeric"
          autoComplete="off"
          required
          maxLength={20}
          hint="숫자 10~14자리. 하이픈은 빼고 보냅니다."
          error={errors.trackingNumber}
          defaultValue={state.values?.trackingNumber}
        />
        <SubmitButton className="sm:mt-6" pendingText="발송 처리 중…">
          발송
        </SubmitButton>
      </div>
    </form>
  );
}
