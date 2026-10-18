/**
 * Server Action이 폼에 돌려주는 결과(ADR 0015). `useActionState`의 상태로 쓴다.
 * 필드 오류는 zod 검증이나 서비스 응답(ProblemDetail의 `errors`)에서 온다.
 */
export type FormState = {
  status: "idle" | "success" | "error";
  /** 폼 위에 보여 줄 문구 */
  message?: string;
  /** 필드 이름 → 오류 문구 */
  fieldErrors?: Record<string, string>;
  /** 실패했을 때 사용자가 넣은 값. 다시 그릴 때 입력을 지우지 않는다(비밀번호는 넣지 않는다) */
  values?: Record<string, string>;
};

export const idleState: FormState = { status: "idle" };

type Issue = { path: PropertyKey[]; message: string };

/** zod의 `issues`를 필드 오류로 바꾼다. 필드마다 첫 오류만 쓴다. zod에 의존하지 않도록 구조만 받는다. */
export function fieldErrorsFrom(issues: readonly Issue[]): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const issue of issues) {
    const field = issue.path.map(String).join(".");
    errors[field] ??= issue.message;
  }
  return errors;
}

/** 서비스 오류 응답(RFC 9457 ProblemDetail, docs/architecture/conventions.md "오류"). */
export type Problem = {
  status?: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: { field: string; message: string }[];
};

/** openapi-fetch의 `error`와 응답 상태를 폼 결과로 바꾼다. 서비스가 준 문구를 쓰고, 없으면 상태별 기본 문구를 쓴다. */
export function problemState(
  error: unknown,
  status: number,
  values?: Record<string, string>,
): FormState {
  const problem = (typeof error === "object" && error !== null ? error : {}) as Problem;
  const fieldErrors: Record<string, string> = {};
  for (const item of problem.errors ?? []) {
    fieldErrors[item.field] ??= item.message;
  }
  return {
    status: "error",
    message: problem.detail ?? defaultMessage(status),
    ...(Object.keys(fieldErrors).length > 0 ? { fieldErrors } : {}),
    ...(values ? { values } : {}),
  };
}

function defaultMessage(status: number): string {
  if (status === 429) {
    return "요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요.";
  }
  if (status === 401) {
    return "로그인이 필요합니다.";
  }
  if (status === 403) {
    return "이 작업을 할 권한이 없습니다.";
  }
  if (status >= 500) {
    return "일시적인 오류입니다. 잠시 뒤 다시 시도해 주세요.";
  }
  return "요청을 처리하지 못했습니다.";
}
