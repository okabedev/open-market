"use client";

import { FormAlert, idleState, SubmitButton, TextAreaField } from "@openmarket/ui";
import { useActionState } from "react";
import { REPLY_MAX } from "@/lib/review";
import { writeReply } from "./actions";

/**
 * 리뷰 답글 폼(REV-3). 목록의 리뷰마다 하나라 입력 id를 리뷰 ID로 만든다. 답글이 있으면 그 본문을 채워 고친다. 리뷰를 쓴 구매자와 상품
 * 상세를 보는 모두에게 보인다.
 */
export function ReplyForm({ reviewId, current }: { reviewId: string; current?: string }) {
  const [state, formAction] = useActionState(writeReply, idleState);
  return (
    <form action={formAction} className="grid gap-2">
      <input type="hidden" name="reviewId" value={reviewId} />
      <FormAlert state={state} />
      <TextAreaField
        id={`reply-${reviewId}`}
        name="content"
        label={current ? "답글 고치기" : "답글"}
        required
        maxLength={REPLY_MAX}
        defaultValue={state.values?.content ?? current}
        error={state.fieldErrors?.content}
        hint={`${REPLY_MAX.toLocaleString("ko-KR")}자까지. 상품 상세에 리뷰와 함께 보입니다.`}
      />
      <div>
        <SubmitButton size="sm" pendingText="저장하는 중…">
          {current ? "답글 고치기" : "답글 달기"}
        </SubmitButton>
      </div>
    </form>
  );
}
