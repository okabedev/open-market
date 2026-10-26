const KST_OFFSET_MS = 9 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

/** 한국 시간으로 옮긴 시각. UTC 필드로 한국 날짜·시각을 읽는다(서머타임 없음). */
function kst(iso: string): Date {
  return new Date(new Date(iso).getTime() + KST_OFFSET_MS);
}

function day(date: Date, withYear: boolean): string {
  const text = `${date.getUTCMonth() + 1}월 ${date.getUTCDate()}일`;
  return withYear ? `${date.getUTCFullYear()}년 ${text}` : text;
}

function time(date: Date): string {
  return date.toISOString().slice(11, 16);
}

/**
 * 정산 기간 `[시작, 끝)` 문구(docs/architecture/settlement.md "정산 기간"). 한국 시간 자정에 나뉜 기간은 날짜로 쓰고, 끝은 기간에 들지
 * 않아 그 전날을 끝 날로 쓴다(알림 문구와 같다). 해가 바뀌면 두 날짜에 연도를 쓴다. 자정에 나뉘지 않은 짧은 기간(E2E)은 시각까지 쓴다.
 */
export function periodLabel(periodStart: string, periodEnd: string): string {
  const start = kst(periodStart);
  const end = kst(periodEnd);
  const midnight = (date: Date) => date.getTime() % DAY_MS === 0;
  if (!midnight(start) || !midnight(end)) {
    const sameDay = start.toISOString().slice(0, 10) === end.toISOString().slice(0, 10);
    return sameDay
      ? `${day(start, false)} ${time(start)}~${time(end)}`
      : `${day(start, false)} ${time(start)}~${day(end, false)} ${time(end)}`;
  }
  const last = new Date(end.getTime() - DAY_MS);
  if (last.getTime() === start.getTime()) {
    return day(start, false);
  }
  const crossesYear = start.getUTCFullYear() !== last.getUTCFullYear();
  return `${day(start, crossesYear)}~${day(last, crossesYear)}`;
}

const STATUS_LABELS = {
  CONFIRMED: "지급 대기",
  PAID_OUT: "지급 완료",
} as const;

/** 정산 상태의 화면 이름. 확정(`CONFIRMED`)은 관리자가 지급하기를 기다린다. */
export function settlementStatusLabel(status: keyof typeof STATUS_LABELS): string {
  return STATUS_LABELS[status];
}
