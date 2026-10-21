"use client";

import { FormAlert, formatPrice, idleState, SubmitButton } from "@openmarket/ui";
import { useActionState } from "react";
import type { CheckoutLine } from "@/lib/checkout";
import { placeOrder } from "./actions";

export type CheckoutAddress = {
  addressId: string;
  label: string;
  recipient: string;
  address: string;
  isDefault: boolean;
};

type CheckoutFormProps = {
  lines: CheckoutLine[];
  addresses: CheckoutAddress[];
  amount: number;
  /** 주문서를 그릴 때 한 번 만든다. 두 번 눌러도 주문은 하나다 */
  idempotencyKey: string;
};

/** 주문서의 배송지 선택과 주문 버튼. 줄은 숨은 값으로 함께 보낸다. */
export function CheckoutForm({ lines, addresses, amount, idempotencyKey }: CheckoutFormProps) {
  const [state, action] = useActionState(placeOrder, idleState);
  const addressError = state.fieldErrors?.addressId;
  return (
    <form action={action} className="grid gap-4">
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      {lines.map((line) => (
        <span key={line.skuId} hidden>
          <input type="hidden" name="skuId" value={line.skuId} />
          <input type="hidden" name="quantity" value={line.quantity} />
          <input type="hidden" name="expectedPrice" value={line.expectedPrice} />
        </span>
      ))}
      <FormAlert state={state} />
      <fieldset
        className="grid gap-2"
        aria-describedby={addressError ? "address-error" : undefined}
      >
        <legend className="mb-2 font-medium">배송지</legend>
        {addresses.map((address) => (
          <label
            key={address.addressId}
            className="flex cursor-pointer items-start gap-3 rounded-md border p-3 text-sm has-[:checked]:border-primary"
          >
            <input
              type="radio"
              name="addressId"
              value={address.addressId}
              defaultChecked={address.isDefault}
              className="mt-1"
            />
            <span className="grid gap-0.5">
              <span className="font-medium">{address.label}</span>
              <span>{address.recipient}</span>
              <span className="text-muted-foreground">{address.address}</span>
            </span>
          </label>
        ))}
        {addressError ? (
          <p id="address-error" className="text-xs text-destructive">
            {addressError}
          </p>
        ) : null}
      </fieldset>
      <SubmitButton size="lg" pendingText="주문하는 중…">
        {formatPrice(amount)} 주문하고 결제하기
      </SubmitButton>
    </form>
  );
}
