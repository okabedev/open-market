/**
 * 운영 지표 화면(ADM-4, ADR 0023)의 기간과 표. 주문 지표는 order, 결제 지표는 payment가 날짜(KST)별로 센다
 * (docs/architecture/order.md·payment.md "운영 지표"). 화면은 두 응답을 날짜로 합치고, 한 서비스가 응답하지 않으면 그쪽 칸만 비운다.
 */

/** 서비스가 받는 기간 상한. 넘으면 서비스가 400이라 화면에서 먼저 막는다. */
export const MAX_DAYS = 31;
export const DEFAULT_DAYS = 7;

const DAY_MS = 24 * 60 * 60 * 1000;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const kstDate = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" });

export type OrderDay = { orderCount: number; paidOrderCount: number; gmv: number };
export type PaymentDay = {
  approvedCount: number;
  rejectedCount: number;
  undecidedCount: number;
  /** 거절 ÷ (승인 + 거절). 정해진 시도가 없으면 null이다. */
  failureRate: number | null;
};
export type MetricsRow = { date: string; order: OrderDay | null; payment: PaymentDay | null };
export type MetricsPeriod = { from: string; to: string; error: string | null };

type OrderDayView = { date?: string; orderCount?: number; paidOrderCount?: number; gmv?: number };
type PaymentDayView = {
  date?: string;
  approvedCount?: number;
  rejectedCount?: number;
  undecidedCount?: number;
  failureRate?: number;
};

/** 그 시각의 한국 날짜(`yyyy-MM-dd`). */
export function kstToday(now: Date): string {
  return kstDate.format(now);
}

function parse(date: string): number | null {
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

/** 검색 조건의 기간. 없으면 오늘(KST)까지 최근 7일이고, 한쪽만 있으면 나머지를 7일 기간으로 채운다. */
export function metricsPeriod(
  params: Record<string, string | string[] | undefined>,
  now: Date,
): MetricsPeriod {
  const { from: fromParam, to: toParam } = params;
  if (Array.isArray(fromParam) || Array.isArray(toParam)) {
    return { from: "", to: "", error: "날짜를 하나씩 입력해 주세요." };
  }
  const to = toParam ?? (fromParam ? addDays(fromParam, DEFAULT_DAYS - 1) : kstToday(now));
  const from = fromParam ?? addDays(to, -(DEFAULT_DAYS - 1));
  const fromTime = parse(from);
  const toTime = parse(to);
  if (fromTime === null || toTime === null) {
    return { from, to, error: "날짜를 yyyy-MM-dd 형식의 있는 날짜로 입력해 주세요." };
  }
  if (fromTime > toTime) {
    return { from, to, error: "시작일이 종료일보다 늦습니다." };
  }
  if ((toTime - fromTime) / DAY_MS + 1 > MAX_DAYS) {
    return { from, to, error: `기간은 ${MAX_DAYS}일까지입니다.` };
  }
  return { from, to, error: null };
}

/** 기간의 날마다 한 행. 응답이 없는(null) 서비스의 칸은 null이고, 응답에 없는 날은 0이다. */
export function mergeDays(
  from: string,
  to: string,
  orders: OrderDayView[] | null,
  payments: PaymentDayView[] | null,
): MetricsRow[] {
  const orderByDate = new Map(orders?.map((day) => [day.date, day]));
  const paymentByDate = new Map(payments?.map((day) => [day.date, day]));
  const rows: MetricsRow[] = [];
  for (let date = from; date <= to; date = addDays(date, 1)) {
    const order = orderByDate.get(date);
    const payment = paymentByDate.get(date);
    rows.push({
      date,
      order: orders
        ? {
            orderCount: order?.orderCount ?? 0,
            paidOrderCount: order?.paidOrderCount ?? 0,
            gmv: order?.gmv ?? 0,
          }
        : null,
      payment: payments
        ? {
            approvedCount: payment?.approvedCount ?? 0,
            rejectedCount: payment?.rejectedCount ?? 0,
            undecidedCount: payment?.undecidedCount ?? 0,
            failureRate: payment?.failureRate ?? null,
          }
        : null,
    });
  }
  return rows;
}

function failureRate(approved: number, rejected: number): number | null {
  const decided = approved + rejected;
  return decided === 0 ? null : Math.round((rejected / decided) * 10_000) / 10_000;
}

/** 기간 합계. 실패율은 날짜별 실패율의 평균이 아니라 기간 전체의 거절 ÷ (승인 + 거절)이다. */
export function totals(rows: MetricsRow[]): { order: OrderDay | null; payment: PaymentDay | null } {
  const orders = rows.map((row) => row.order).filter((day) => day !== null);
  const payments = rows.map((row) => row.payment).filter((day) => day !== null);
  const sum = <T>(days: T[], value: (day: T) => number) =>
    days.reduce((total, day) => total + value(day), 0);
  const approvedCount = sum(payments, (day) => day.approvedCount);
  const rejectedCount = sum(payments, (day) => day.rejectedCount);
  return {
    order:
      rows.length > 0 && orders.length === rows.length
        ? {
            orderCount: sum(orders, (day) => day.orderCount),
            paidOrderCount: sum(orders, (day) => day.paidOrderCount),
            gmv: sum(orders, (day) => day.gmv),
          }
        : null,
    payment:
      rows.length > 0 && payments.length === rows.length
        ? {
            approvedCount,
            rejectedCount,
            undecidedCount: sum(payments, (day) => day.undecidedCount),
            failureRate: failureRate(approvedCount, rejectedCount),
          }
        : null,
  };
}

/** 실패율(0~1)을 백분율 소수 둘째 자리로. 값이 없으면 "-"다. */
export function formatRate(rate: number | null | undefined): string {
  return rate === null || rate === undefined ? "-" : `${(rate * 100).toFixed(2)}%`;
}
