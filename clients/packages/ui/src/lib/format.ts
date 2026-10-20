const dateTime = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Seoul",
});

const price = new Intl.NumberFormat("ko-KR");

/** 원 단위 금액. 예: 19,900원. 값이 없으면 "-"다. */
export function formatPrice(won: number | undefined | null): string {
  return won === undefined || won === null ? "-" : `${price.format(won)}원`;
}

/** ISO 시각을 한국 시간으로 보여 준다. 값이 없으면 "-"다. */
export function formatDateTime(iso: string | undefined | null): string {
  return iso ? dateTime.format(new Date(iso)) : "-";
}
