import type { ComponentProps, ReactNode } from "react";
import { Input, Textarea } from "./input";
import { Label } from "./label";
import { cn } from "./lib/cn";

type FieldBaseProps = {
  /** 폼 필드 이름 */
  name: string;
  /**
   * 입력 id. 안내·오류 문구 id도 여기서 만든다. 기본은 `name`이다. 같은 이름의 필드가 한 화면에 여럿이면(목록 행마다 있는 폼) 따로
   * 준다
   */
  id?: string | undefined;
  label: string;
  /** 서버가 돌려준 이 필드의 오류 */
  error?: string | undefined;
  hint?: ReactNode;
  className?: string;
};

function FieldShell({
  id,
  label,
  error,
  hint,
  className,
  children,
}: Omit<FieldBaseProps, "name"> & { id: string; children: ReactNode }) {
  return (
    <div className={cn("grid gap-1.5", className)}>
      <Label htmlFor={id}>{label}</Label>
      {children}
      {hint && !error ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function describedBy(id: string, error?: string, hint?: ReactNode): string | undefined {
  if (error) {
    return `${id}-error`;
  }
  return hint ? `${id}-hint` : undefined;
}

/** 이름표, 입력, 안내, 오류를 한 묶음으로 그린다. 오류가 있으면 입력에 `aria-invalid`와 `aria-describedby`를 붙인다. */
export function TextField({
  name,
  id = name,
  label,
  error,
  hint,
  className,
  ...input
}: FieldBaseProps & Omit<ComponentProps<"input">, "name" | "id" | "className">) {
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={className}>
      <Input
        id={id}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        {...input}
      />
    </FieldShell>
  );
}

export function TextAreaField({
  name,
  id = name,
  label,
  error,
  hint,
  className,
  ...textarea
}: FieldBaseProps & Omit<ComponentProps<"textarea">, "name" | "id" | "className">) {
  return (
    <FieldShell id={id} label={label} error={error} hint={hint} className={className}>
      <Textarea
        id={id}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(id, error, hint)}
        {...textarea}
      />
    </FieldShell>
  );
}
