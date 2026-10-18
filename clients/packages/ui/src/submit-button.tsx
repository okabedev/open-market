"use client";

import type { ComponentProps } from "react";
import { useFormStatus } from "react-dom";
import { Button } from "./button";

/** 폼 제출 버튼. 제출하는 동안 누를 수 없고 `pendingText`를 보여 준다. */
export function SubmitButton({
  children,
  pendingText = "처리 중…",
  disabled,
  ...props
}: ComponentProps<typeof Button> & { pendingText?: string }) {
  const { pending } = useFormStatus();
  return (
    <Button
      type="submit"
      disabled={pending || disabled}
      aria-busy={pending || undefined}
      {...props}
    >
      {pending ? pendingText : children}
    </Button>
  );
}
