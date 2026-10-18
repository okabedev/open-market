import { z } from "zod";

/** member의 입력 규칙(docs/architecture/member.md "규칙")과 같다. 최종 판단은 member가 한다. */
export const phonePattern = /^0\d{1,2}-\d{3,4}-\d{4}$/;

export const nameField = z
  .string()
  .trim()
  .min(1, "이름을 입력해 주세요.")
  .max(50, "이름은 50자까지입니다.");

export const phoneField = z
  .string()
  .trim()
  .regex(phonePattern, "전화번호는 010-1234-5678 형식으로 입력해 주세요.");

/** 폼 값을 문자열로 읽는다. 없으면 빈 문자열이다. */
export function text(form: FormData, name: string): string {
  const value = form.get(name);
  return typeof value === "string" ? value : "";
}
