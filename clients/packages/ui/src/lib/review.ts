/** 별점(1~5)을 다섯 칸 별로 그린다. 화면 읽기 프로그램에는 `aria-label`로 숫자를 따로 준다. */
export function stars(rating: number): string {
  const filled = Math.min(5, Math.max(0, Math.round(rating)));
  return "★".repeat(filled) + "☆".repeat(5 - filled);
}
