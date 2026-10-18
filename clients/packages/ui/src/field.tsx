import type { ComponentProps, ReactNode } from "react";
import { Input, Textarea } from "./input";
import { Label } from "./label";
import { cn } from "./lib/cn";

type FieldBaseProps = {
  /** 폼 필드 이름. id와 오류 메시지 id도 여기서 만든다 */
  name: string;
  label: string;
  /** 서버가 돌려준 이 필드의 오류 */
  error?: string | undefined;
  hint?: ReactNode;
  className?: string;
};

function FieldShell({
  name,
  label,
  error,
  hint,
  className,
  children,
}: FieldBaseProps & { children: ReactNode }) {
  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={name}>{label}</Label>
      {children}
      {hint && !error ? (
        <p id={`${name}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${name}-error`} className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function describedBy(name: string, error?: string, hint?: ReactNode): string | undefined {
  if (error) {
    return `${name}-error`;
  }
  return hint ? `${name}-hint` : undefined;
}

/** 이름표, 입력, 안내, 오류를 한 묶음으로 그린다. 오류가 있으면 입력에 `aria-invalid`와 `aria-describedby`를 붙인다. */
export function TextField({
  name,
  label,
  error,
  hint,
  className,
  ...input
}: FieldBaseProps & Omit<ComponentProps<"input">, "name" | "id" | "className">) {
  return (
    <FieldShell name={name} label={label} error={error} hint={hint} className={className}>
      <Input
        id={name}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(name, error, hint)}
        {...input}
      />
    </FieldShell>
  );
}

export function TextAreaField({
  name,
  label,
  error,
  hint,
  className,
  ...textarea
}: FieldBaseProps & Omit<ComponentProps<"textarea">, "name" | "id" | "className">) {
  return (
    <FieldShell name={name} label={label} error={error} hint={hint} className={className}>
      <Textarea
        id={name}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(name, error, hint)}
        {...textarea}
      />
    </FieldShell>
  );
}
