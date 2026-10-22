"use client";

import { ConfirmSubmit, FormAlert, idleState, SubmitButton, TextAreaField } from "@openmarket/ui";
import { useActionState } from "react";
import { approveClaim, rejectClaim } from "../actions";

type ClaimDecisionProps = {
  sellerOrderId: string;
  claimId: string;
  type: "RETURN" | "EXCHANGE";
};

/**
 * 판매자가 기다리는 반품·교환의 승인과 거부(CLM-3). 회수품을 검수한 뒤 고른다. 반품 승인은 재입고 여부를 함께 고르고, 거부는 사유를
 * 남긴다.
 */
export function ClaimDecision({ sellerOrderId, claimId, type }: ClaimDecisionProps) {
  const [approveState, approveAction] = useActionState(approveClaim, idleState);
  const [rejectState, rejectAction] = useActionState(rejectClaim, idleState);
  const restockError = approveState.fieldErrors?.restock;
  const hidden = (
    <>
      <input type="hidden" name="sellerOrderId" value={sellerOrderId} />
      <input type="hidden" name="claimId" value={claimId} />
      <input type="hidden" name="type" value={type} />
    </>
  );
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <form action={approveAction} className="grid content-start gap-3 rounded-md border p-4">
        {hidden}
        <h3 className="font-medium">승인</h3>
        <FormAlert state={approveState} />
        {type === "RETURN" ? (
          <fieldset
            className="grid gap-2 text-sm"
            aria-describedby={restockError ? `restock-error-${claimId}` : undefined}
          >
            <legend className="mb-1">회수한 상품을 재고에 다시 넣을까요?</legend>
            <label className="flex items-center gap-2">
              <input type="radio" name="restock" value="true" />
              재입고한다(다시 팔 수 있다)
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="restock" value="false" />
              재입고하지 않는다(불량·훼손)
            </label>
            {restockError ? (
              <p id={`restock-error-${claimId}`} className="text-xs text-destructive">
                {restockError}
              </p>
            ) : null}
          </fieldset>
        ) : (
          <p className="text-sm text-muted-foreground">
            승인하면 같은 상품을 다시 보낼 배송이 생깁니다. 발주 확인과 발송을 다시 해 주세요.
          </p>
        )}
        <div>
          <ConfirmSubmit
            size="sm"
            title={type === "RETURN" ? "반품을 승인할까요?" : "교환을 승인할까요?"}
            description={
              type === "RETURN"
                ? "하위 주문 금액을 환불합니다. 구매자 귀책(단순 변심)이면 반품 배송비를 뺍니다. 되돌릴 수 없습니다."
                : "같은 상품을 다시 보낼 배송이 생깁니다. 되돌릴 수 없습니다."
            }
            confirmText="승인"
          >
            승인
          </ConfirmSubmit>
        </div>
      </form>
      <form action={rejectAction} className="grid content-start gap-3 rounded-md border p-4">
        {hidden}
        <h3 className="font-medium">거부</h3>
        <FormAlert state={rejectState} />
        <TextAreaField
          name="reason"
          id={`reason-${claimId}`}
          label="거부 사유"
          required
          maxLength={500}
          hint="구매자에게 보입니다. 구매자는 관리자 중재를 요청할 수 있습니다."
          error={rejectState.fieldErrors?.reason}
          defaultValue={rejectState.values?.reason}
        />
        <div>
          <SubmitButton size="sm" variant="destructive" pendingText="거부하는 중…">
            거부
          </SubmitButton>
        </div>
      </form>
    </div>
  );
}
