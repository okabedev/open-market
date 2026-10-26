/**
 * 리뷰 작성 규칙의 상한. 클라이언트 폼도 쓰므로 zod에 의존하지 않는다(검증 스키마는 `app/my/reviews/actions.ts`). 원본은 review
 * 규칙이다(docs/architecture/review.md "리뷰").
 */
export const REVIEW_LIMITS = {
  photos: 5,
  contentMin: 10,
  contentMax: 1000,
} as const;

/** 별점(1~5)을 다섯 칸 별로 그린다. 화면 읽기 프로그램에는 `aria-label`로 숫자를 따로 준다. */
export function stars(rating: number): string {
  const filled = Math.min(5, Math.max(0, Math.round(rating)));
  return "★".repeat(filled) + "☆".repeat(5 - filled);
}

/** 별점 요약 문구. 평균은 소수 첫째 자리다(review `ratingAverage`). 리뷰가 없으면 "리뷰 없음". */
export function ratingLabel(
  average: number | null | undefined,
  count: number | null | undefined,
): string {
  if (average === null || average === undefined || !count) {
    return "리뷰 없음";
  }
  return `${average.toFixed(1)} (리뷰 ${count.toLocaleString("ko-KR")}개)`;
}
