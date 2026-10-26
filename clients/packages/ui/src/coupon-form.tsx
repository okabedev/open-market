"use client";

import { useActionState, useState, type ReactNode } from "react";
import { TextField } from "./field";
import { FormAlert } from "./form-alert";
import { idleState, type FormState } from "./form-state";
import { COUPON_LIMITS } from "./lib/coupon-form";
import { SubmitButton } from "./submit-button";

type CouponFormProps = {
  /** 쿠폰을 만드는 Server Action. 앱마다 판매자·관리자 경로를 부른다 */
  action: (state: FormState, form: FormData) => Promise<FormState>;
  /** 화면을 그릴 때 만든 멱등성 키. 두 번 눌러도 하나만 생긴다 */
  idempotencyKey: string;
  /** 발급 시작·끝 기본값(한국 시간 `datetime-local`) */
  defaults: { issueStartAt: string; issueEndAt: string };
  /** 최소 주문 금액 안내. 무엇의 합과 비교하는지 */
  minOrderHint: string;
  /** 폼 아래 안내. 어디에 쓰는 쿠폰이고 누가 부담하는지 */
  scopeNote: ReactNode;
};

/**
 * 쿠폰 만들기 폼(PRM-1, 판매자 쿠폰과 플랫폼 쿠폰). 할인 방식에 따라 할인 값의 단위(원, %)가 바뀌고 최대 할인 칸은 정률일 때만
 * 보인다. 시각은 한국 시간으로 받는다. 검사는 Server Action이 `readCouponForm`으로 한다.
 */
export function CouponForm({
  action,
  idempotencyKey,
  defaults,
  minOrderHint,
  scopeNote,
}: CouponFormProps) {
  const [state, formAction] = useActionState(action, idleState);
  const errors = state.fieldErrors ?? {};
  const values = state.values ?? {};
  const [discountType, setDiscountType] = useState(
    values.discountType === "RATE" ? "RATE" : "FIXED",
  );

  return (
    <form action={formAction} className="grid max-w-2xl gap-5">
      <input type="hidden" name="idempotencyKey" value={idempotencyKey} />
      <FormAlert state={state} />

      <TextField
        name="name"
        label="쿠폰 이름"
        required
        maxLength={COUPON_LIMITS.name}
        defaultValue={values.name}
        error={errors.name}
        hint={`${COUPON_LIMITS.name}자까지. 구매자에게 보입니다.`}
      />

      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">할인 방식</legend>
        <div className="flex flex-wrap gap-2">
          {(
            [
              ["FIXED", "정액(원)"],
              ["RATE", "정률(%)"],
            ] as const
          ).map(([value, label]) => (
            <label
              key={value}
              className="flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm has-[:checked]:border-primary"
            >
              <input
                type="radio"
                name="discountType"
                value={value}
                checked={discountType === value}
                onChange={() => setDiscountType(value)}
              />
              {label}
            </label>
          ))}
        </div>
        {errors.discountType ? (
          <p className="text-xs text-destructive">{errors.discountType}</p>
        ) : null}
      </fieldset>

      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          name="discountValue"
          label={discountType === "RATE" ? "할인율(%)" : "할인 금액(원)"}
          type="number"
          inputMode="numeric"
          required
          min={1}
          {...(discountType === "RATE" ? { max: COUPON_LIMITS.rate } : {})}
          defaultValue={values.discountValue}
          error={errors.discountValue}
        />
        {discountType === "RATE" ? (
          <TextField
            name="maxDiscount"
            label="최대 할인(원, 선택)"
            type="number"
            inputMode="numeric"
            min={1}
            defaultValue={values.maxDiscount}
            error={errors.maxDiscount}
            hint="비우면 상한이 없습니다."
          />
        ) : null}
        <TextField
          name="minOrderAmount"
          label="최소 주문 금액(원)"
          type="number"
          inputMode="numeric"
          min={0}
          defaultValue={values.minOrderAmount ?? "0"}
          error={errors.minOrderAmount}
          hint={minOrderHint}
        />
        <TextField
          name="quantity"
          label="발급 수량(장)"
          type="number"
          inputMode="numeric"
          required
          min={1}
          max={COUPON_LIMITS.quantity}
          defaultValue={values.quantity}
          error={errors.quantity}
          hint="선착순입니다. 한 사람은 한 장만 받습니다."
        />
        <TextField
          name="issueStartAt"
          label="발급 시작"
          type="datetime-local"
          required
          defaultValue={values.issueStartAt ?? defaults.issueStartAt}
          error={errors.issueStartAt}
          hint="한국 시간. 지난 시각이면 바로 발급합니다."
        />
        <TextField
          name="issueEndAt"
          label="발급 끝"
          type="datetime-local"
          required
          defaultValue={values.issueEndAt ?? defaults.issueEndAt}
          error={errors.issueEndAt}
          hint="한국 시간. 이 시각부터 받을 수 없습니다."
        />
        <TextField
          name="validDays"
          label="사용 기한(발급 후 일)"
          type="number"
          inputMode="numeric"
          required
          min={1}
          max={COUPON_LIMITS.validDays}
          defaultValue={values.validDays ?? "30"}
          error={errors.validDays}
        />
      </div>

      <p className="text-sm text-muted-foreground">{scopeNote}</p>

      <div>
        <SubmitButton pendingText="만드는 중…">쿠폰 만들기</SubmitButton>
      </div>
    </form>
  );
}
