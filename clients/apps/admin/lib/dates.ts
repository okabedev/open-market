/** KST 날짜(`yyyy-MM-dd`) 도우미. 관리자 화면의 기간 조건(운영 지표, 감사 로그)이 쓴다. */

export const DAY_MS = 24 * 60 * 60 * 1000;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const kstDate = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" });

/** 그 시각의 한국 날짜(`yyyy-MM-dd`). */
export function kstToday(now: Date): string {
  return kstDate.format(now);
}

/** `yyyy-MM-dd`인 있는 날짜면 그날 00:00 UTC의 밀리초, 아니면 null이다. */
export function parseDate(date: string): number | null {
  if (!DATE.test(date)) {
    return null;
  }
  const time = Date.parse(`${date}T00:00:00Z`);
  // 2026-02-30처럼 없는 날짜는 다음 달로 넘어가므로 다시 써서 비교한다.
  return Number.isNaN(time) || new Date(time).toISOString().slice(0, 10) !== date ? null : time;
}

/** 날짜(`yyyy-MM-dd`)에 며칠을 더한다. */
export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10);
}

/** KST 날짜의 00:00을 ISO 시각(UTC)으로. */
export function kstStartOf(date: string): string {
  return new Date(Date.parse(`${date}T00:00:00+09:00`)).toISOString();
}
