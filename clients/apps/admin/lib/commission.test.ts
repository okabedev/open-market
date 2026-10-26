import { describe, expect, it } from "vitest";
import { bpToPercent, percentToBp } from "./commission";

describe("percentToBp", () => {
  it("%를 소수 둘째 자리까지 받아 bp(0.01%)로 바꾼다", () => {
    expect(percentToBp("10")).toBe(1_000);
    expect(percentToBp(" 8.5 ")).toBe(850);
    expect(percentToBp("0.05")).toBe(5);
    expect(percentToBp("0")).toBe(0);
    expect(percentToBp("100")).toBe(10_000);
  });

  it("범위 밖, 소수 셋째 자리, 숫자가 아니면 null이다", () => {
    expect(percentToBp("")).toBeNull();
    expect(percentToBp("100.01")).toBeNull();
    expect(percentToBp("-1")).toBeNull();
    expect(percentToBp("8.125")).toBeNull();
    expect(percentToBp("abc")).toBeNull();
    expect(percentToBp("1e2")).toBeNull();
  });
});

describe("bpToPercent", () => {
  it("bp를 % 문구로 쓴다", () => {
    expect(bpToPercent(1_000)).toBe("10%");
    expect(bpToPercent(850)).toBe("8.5%");
    expect(bpToPercent(5)).toBe("0.05%");
  });
});
