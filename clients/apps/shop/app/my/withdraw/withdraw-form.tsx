"use client";

import { ConfirmSubmit, FormAlert, idleState } from "@openmarket/ui";
import { useActionState } from "react";
import { withdraw } from "./actions";

export function WithdrawForm() {
  const [state, action] = useActionState(withdraw, idleState);
  return (
    <form action={action} className="grid gap-4">
      <FormAlert state={state} />
      <div>
        <ConfirmSubmit
          title="정말 탈퇴할까요?"
          description="이름, 연락처, 배송지가 바로 지워지고 되돌릴 수 없습니다."
          confirmText="탈퇴"
          variant="destructive"
        >
          탈퇴하기
        </ConfirmSubmit>
      </div>
    </form>
  );
}
