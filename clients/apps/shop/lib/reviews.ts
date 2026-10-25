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
