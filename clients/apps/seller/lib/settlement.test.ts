import { describe, expect, it } from "vitest";
import { periodLabel, settlementStatusLabel } from "./settlement";

describe("periodLabel", () => {
  it("한국 시간 자정에 나뉜 기간은 날짜로 쓰고, 끝 날은 기간에 들지 않아 그 전날이다", () => {
    // 2026-10-26 00:00 ~ 2026-11-02 00:00(한국 시간)
    expect(periodLabel("2026-10-25T15:00:00Z", "2026-11-01T15:00:00Z")).toBe("10월 26일~11월 1일");
  });

  it("해가 바뀌면 끝 날에도 연도를 쓴다", () => {
    expect(periodLabel("2026-12-27T15:00:00Z", "2027-01-03T15:00:00Z")).toBe(
      "2026년 12월 28일~2027년 1월 3일",
    );
  });

  it("하루 기간은 날짜 하나다", () => {
    expect(periodLabel("2026-10-25T15:00:00Z", "2026-10-26T15:00:00Z")).toBe("10월 26일");
  });

  it("자정에 나뉘지 않은 기간(E2E의 짧은 기간)은 시각까지 쓴다", () => {
    expect(periodLabel("2026-10-26T05:00:00Z", "2026-10-26T05:01:00Z")).toBe(
      "10월 26일 14:00~14:01",
    );
  });
});

describe("settlementStatusLabel", () => {
  it("확정과 지급 완료를 화면 이름으로 바꾼다", () => {
    expect(settlementStatusLabel("CONFIRMED")).toBe("지급 대기");
    expect(settlementStatusLabel("PAID_OUT")).toBe("지급 완료");
  });
});
