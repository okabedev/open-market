const dateTime = new Intl.DateTimeFormat("ko-KR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Asia/Seoul",
});

/** ISO 시각을 한국 시간으로 보여 준다. 값이 없으면 "-"다. */
export function formatDateTime(iso: string | undefined | null): string {
  return iso ? dateTime.format(new Date(iso)) : "-";
}
