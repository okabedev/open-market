"use client";

import * as AlertDialog from "@radix-ui/react-alert-dialog";
import { useRef, type ComponentProps, type ReactNode } from "react";
import { Button, buttonVariants } from "./button";
import { cn } from "./lib/cn";

type ConfirmSubmitProps = {
  /** 폼 안의 트리거 버튼 글자 */
  children: ReactNode;
  title: string;
  description: ReactNode;
  confirmText: string;
  variant?: ComponentProps<typeof Button>["variant"];
  size?: ComponentProps<typeof Button>["size"];
};

/**
 * 되돌릴 수 없는 작업(탈퇴, 승인, 정지)을 한 번 더 묻고 감싼 폼을 제출한다. `<form>` 안에 둔다.
 * 확인 대화상자라 JS가 있어야 제출할 수 있다.
 */
export function ConfirmSubmit({
  children,
  title,
  description,
  confirmText,
  variant = "default",
  size,
}: ConfirmSubmitProps) {
  const trigger = useRef<HTMLButtonElement>(null);
  return (
    <AlertDialog.Root>
      <AlertDialog.Trigger asChild>
        <Button ref={trigger} type="button" variant={variant} size={size}>
          {children}
        </Button>
      </AlertDialog.Trigger>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 z-40 bg-foreground/40" />
        <AlertDialog.Content className="fixed top-1/2 left-1/2 z-50 grid w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 gap-4 rounded-xl border bg-card p-6 shadow-lg">
          <AlertDialog.Title className="text-lg font-semibold">{title}</AlertDialog.Title>
          <AlertDialog.Description className="text-sm text-muted-foreground">
            {description}
          </AlertDialog.Description>
          <div className="flex justify-end gap-2">
            <AlertDialog.Cancel className={buttonVariants({ variant: "outline" })}>
              취소
            </AlertDialog.Cancel>
            <AlertDialog.Action
              className={cn(buttonVariants({ variant }))}
              onClick={() => trigger.current?.form?.requestSubmit()}
            >
              {confirmText}
            </AlertDialog.Action>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  );
}
