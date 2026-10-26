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
  "mediation-approved": "중재로 승인했습니다. 반품은 환불되고, 교환은 판매자가 다시 보냅니다.",
  "mediation-rejected": "거부를 확정했습니다. 구매자에게 사유가 보입니다.",
  "refund-retried": "환불을 다시 시도합니다. 몇 초 안에 결과가 반영됩니다.",
  "cancel-retried": "결제 취소를 다시 시도합니다. 다음 취소 작업 주기에 반영됩니다.",
  "coupon-created":
    "플랫폼 쿠폰을 만들었습니다. 발급 기간이 되면 구매자가 쿠폰 화면에서 받을 수 있습니다.",
  "coupon-stopped": "발급을 멈췄습니다. 이미 받은 쿠폰은 사용 기한까지 쓸 수 있습니다.",
  "review-hidden":
    "리뷰를 숨겼습니다. 상품 상세와 별점에서 빠지고, 검색의 별점은 몇 초 안에 바뀝니다.",
  "commission-set": "수수료율을 정했습니다. 이 뒤에 구매확정되는 주문부터 씁니다.",
  "commission-reset": "기본 수수료율로 돌렸습니다. 이 뒤에 구매확정되는 주문부터 씁니다.",
  "review-shown": "리뷰를 다시 보입니다. 별점에 다시 들어가고, 검색의 별점은 몇 초 안에 바뀝니다.",
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
