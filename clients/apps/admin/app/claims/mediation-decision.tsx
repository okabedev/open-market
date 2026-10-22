"use client";

import { ConfirmSubmit, FormAlert, idleState } from "@openmarket/ui";
import { useActionState, useId } from "react";
import { ReasonForm } from "@/components/reason-form";
import { approveMediation, rejectMediation } from "./actions";

type MediationDecisionProps = {
  claimId: string;
  type: "RETURN" | "EXCHANGE";
  /** 처리 뒤 돌아올 목록(쪽 번호 포함) */
  returnTo: string;
};

/**
 * 중재 결정(CLM-3, ADM-2). 승인은 귀책을 고른다(교환은 판매자 귀책만). 거부 확정은 사유를 남긴다. 판매자의 거부 사유와 사진은 위
 * 카드에서 본다.
 */
export function MediationDecision({ claimId, type, returnTo }: MediationDecisionProps) {
  const [state, formAction] = useActionState(approveMediation, idleState);
  const id = useId();
  const faultError = state.fieldErrors?.fault;
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <form action={formAction} className="grid content-start gap-3 rounded-md border p-4">
        <input type="hidden" name="claimId" value={claimId} />
        <input type="hidden" name="type" value={type} />
        <input type="hidden" name="returnTo" value={returnTo} />
        <h3 className="font-medium">승인</h3>
        <FormAlert state={state} />
        <fieldset
          className="grid gap-2 text-sm"
          aria-describedby={faultError ? `${id}-fault-error` : undefined}
        >
          <legend className="mb-1">귀책</legend>
          <label className="flex items-center gap-2">
            <input type="radio" name="fault" value="SELLER" />
            판매자(전액 환불{type === "EXCHANGE" ? ", 교환 재발송" : ""})
          </label>
          {type === "RETURN" ? (
            <label className="flex items-center gap-2">
              <input type="radio" name="fault" value="BUYER" />
              구매자(반품 배송비를 빼고 환불)
            </label>
          ) : null}
          {faultError ? (
            <p id={`${id}-fault-error`} className="text-xs text-destructive">
              {faultError}
            </p>
          ) : null}
        </fieldset>
        <div>
          <ConfirmSubmit
            size="sm"
            title={type === "RETURN" ? "반품을 승인할까요?" : "교환을 승인할까요?"}
            description={
              type === "RETURN"
                ? "고른 귀책으로 환불합니다. 재입고하지 않습니다. 되돌릴 수 없습니다."
                : "같은 상품을 다시 보낼 배송이 생깁니다. 되돌릴 수 없습니다."
            }
            confirmText="승인"
          >
            승인
          </ConfirmSubmit>
        </div>
      </form>
      <div className="rounded-md border p-4">
        <h3 className="mb-3 font-medium">거부 확정</h3>
        <ReasonForm
          action={rejectMediation}
          hidden={{ claimId, returnTo }}
          label="거부 확정 사유"
          submitText="거부 확정"
        />
      </div>
    </div>
  );
}
