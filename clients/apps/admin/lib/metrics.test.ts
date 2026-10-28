import { describe, expect, it } from "vitest";
import { formatRate, kstToday, mergeDays, metricsPeriod, totals } from "./metrics";

// 2026-10-27 15:30 UTC = 2026-10-28 00:30 KST
const NOW = new Date("2026-10-27T15:30:00Z");

describe("kstToday", () => {
  it("KST 날짜를 쓴다", () => {
    expect(kstToday(NOW)).toBe("2026-10-28");
    expect(kstToday(new Date("2026-10-27T14:59:59Z"))).toBe("2026-10-27");
  });
});

describe("metricsPeriod", () => {
  it("조건이 없으면 오늘(KST)까지 최근 7일이다", () => {
    expect(metricsPeriod({}, NOW)).toEqual({ from: "2026-10-22", to: "2026-10-28", error: null });
  });

  it("주어진 기간을 그대로 쓴다. 31일까지 된다", () => {
    expect(metricsPeriod({ from: "2026-10-01", to: "2026-10-31" }, NOW)).toEqual({
      from: "2026-10-01",
      to: "2026-10-31",
      error: null,
    });
  });

  it("한쪽만 있으면 나머지는 7일 기간으로 채운다", () => {
    expect(metricsPeriod({ to: "2026-10-10" }, NOW)).toMatchObject({
      from: "2026-10-04",
      to: "2026-10-10",
    });
    expect(metricsPeriod({ from: "2026-10-01" }, NOW)).toMatchObject({
      from: "2026-10-01",
      to: "2026-10-07",
    });
  });

  it("형식이 틀리거나 없는 날짜면 오류다", () => {
    expect(metricsPeriod({ from: "2026/10/01", to: "2026-10-02" }, NOW).error).toContain("날짜");
    expect(metricsPeriod({ from: "2026-02-30", to: "2026-03-02" }, NOW).error).toContain("날짜");
    expect(metricsPeriod({ from: ["2026-10-01", "2026-10-02"] }, NOW).error).toContain("날짜");
  });

  it("시작일이 종료일보다 늦거나 31일을 넘으면 오류다", () => {
    expect(metricsPeriod({ from: "2026-10-02", to: "2026-10-01" }, NOW).error).toContain(
      "늦습니다",
    );
    expect(metricsPeriod({ from: "2026-10-01", to: "2026-11-01" }, NOW).error).toContain("31일");
  });
});

describe("mergeDays", () => {
  const orders = [
    { date: "2026-10-01", orderCount: 3, paidOrderCount: 1, gmv: 30_000 },
    { date: "2026-10-02", orderCount: 0, paidOrderCount: 0, gmv: 0 },
  ];
  const payments = [
    {
      date: "2026-10-01",
      approvedCount: 1,
      rejectedCount: 2,
      undecidedCount: 0,
      failureRate: 0.6667,
    },
    { date: "2026-10-02", approvedCount: 0, rejectedCount: 0, undecidedCount: 1 },
  ];

  it("기간의 날마다 주문과 결제 지표를 한 행으로 합친다", () => {
    expect(mergeDays("2026-10-01", "2026-10-02", orders, payments)).toEqual([
      {
        date: "2026-10-01",
        order: { orderCount: 3, paidOrderCount: 1, gmv: 30_000 },
        payment: { approvedCount: 1, rejectedCount: 2, undecidedCount: 0, failureRate: 0.6667 },
      },
      {
        date: "2026-10-02",
        order: { orderCount: 0, paidOrderCount: 0, gmv: 0 },
        payment: { approvedCount: 0, rejectedCount: 0, undecidedCount: 1, failureRate: null },
      },
    ]);
  });

  it("한 서비스가 응답하지 않으면 그쪽 칸만 비운다", () => {
    const rows = mergeDays("2026-10-01", "2026-10-02", orders, null);
    expect(rows.map((row) => row.payment)).toEqual([null, null]);
    expect(rows[0]?.order?.gmv).toBe(30_000);
  });

  it("월말을 넘는 기간도 날짜를 빠짐없이 센다", () => {
    expect(mergeDays("2026-10-30", "2026-11-02", [], []).map((row) => row.date)).toEqual([
      "2026-10-30",
      "2026-10-31",
      "2026-11-01",
      "2026-11-02",
    ]);
  });
});

describe("totals", () => {
  it("합계와 기간 전체 실패율(거절 ÷ 승인·거절)을 낸다", () => {
    const rows = mergeDays(
      "2026-10-01",
      "2026-10-02",
      [
        { date: "2026-10-01", orderCount: 3, paidOrderCount: 1, gmv: 30_000 },
        { date: "2026-10-02", orderCount: 2, paidOrderCount: 2, gmv: 50_000 },
      ],
      [
        { date: "2026-10-01", approvedCount: 1, rejectedCount: 2, undecidedCount: 0 },
        { date: "2026-10-02", approvedCount: 5, rejectedCount: 0, undecidedCount: 1 },
      ],
    );
    expect(totals(rows)).toEqual({
      order: { orderCount: 5, paidOrderCount: 3, gmv: 80_000 },
      payment: { approvedCount: 6, rejectedCount: 2, undecidedCount: 1, failureRate: 0.25 },
    });
  });

  it("정해진 시도가 없으면 실패율을 비우고, 응답하지 않은 서비스의 합계도 비운다", () => {
    const rows = mergeDays("2026-10-01", "2026-10-01", null, [
      { date: "2026-10-01", approvedCount: 0, rejectedCount: 0, undecidedCount: 2 },
    ]);
    expect(totals(rows)).toEqual({
      order: null,
      payment: { approvedCount: 0, rejectedCount: 0, undecidedCount: 2, failureRate: null },
    });
  });
});

describe("formatRate", () => {
  it("백분율 소수 둘째 자리로 보이고, 값이 없으면 -다", () => {
    expect(formatRate(0.6667)).toBe("66.67%");
    expect(formatRate(0)).toBe("0.00%");
    expect(formatRate(null)).toBe("-");
  });
});
