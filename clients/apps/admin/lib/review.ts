import { problemState, type FormState } from "@openmarket/ui";
import { redirect } from "next/navigation";
import { z } from "zod";

export const reasonSchema = z.object({
  reason: z.string().trim().min(1, "사유를 입력해 주세요.").max(500, "사유는 500자까지입니다."),
});

/** 처리 결과 문구. 목록 화면이 `?notice=`로 받아 위에 보여 준다. 처리한 행은 목록에서 빠질 수 있어서 행 안에 보여 주지 않는다. */
export const NOTICES = {
  approved: "승인했습니다. 신청자는 다음 토큰 갱신부터 판매자 센터에 들어갈 수 있습니다.",
  rejected: "반려했습니다.",
  suspended: "정지했습니다.",
  reinstated: "정지를 풀었습니다.",
  banned: "상품을 제재했습니다. 구매자 검색에서 몇 초 안에 빠집니다.",
} as const;

export type Notice = keyof typeof NOTICES;

/** 같은 목록 화면으로만 돌아간다. 폼의 `returnTo`는 화면이 넣은 값이지만 그대로 믿지 않는다. */
export function returnPath(
  returnTo: FormDataEntryValue | null,
  basePath: string,
  notice: Notice,
): string {
  const path =
    typeof returnTo === "string" && returnTo.startsWith(`${basePath}`) ? returnTo : basePath;
  const url = new URL(path, "http://admin.local");
  if (url.pathname !== basePath) {
    return `${basePath}?notice=${notice}`;
  }
  url.searchParams.set("notice", notice);
  return `${url.pathname}${url.search}`;
}

/** 관리자 작업 응답. 실패는 폼 결과로, 성공은 목록으로 돌아간다. 같은 결과로 다시 해도 서비스는 204다. */
export function reviewResult(
  error: unknown,
  response: Response,
  form: FormData,
  basePath: string,
  notice: Notice,
): FormState {
  if (!response.ok) {
    return problemState(error, response.status);
  }
  redirect(returnPath(form.get("returnTo"), basePath, notice));
}

export function noticeMessage(value: string | string[] | undefined): string | null {
  return typeof value === "string" && value in NOTICES ? NOTICES[value as Notice] : null;
}
