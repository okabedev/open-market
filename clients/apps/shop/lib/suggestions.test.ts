import { describe, expect, it } from "vitest";
import { suggestionKeyword } from "./suggestions";

describe("suggestionKeyword", () => {
  it("앞뒤 공백을 지운 검색어를 쓴다", () => {
    expect(suggestionKeyword("  반팔 ")).toBe("반팔");
  });

  it("비었거나 50자를 넘으면 자동완성을 부르지 않는다", () => {
    expect(suggestionKeyword("   ")).toBeNull();
    expect(suggestionKeyword(null)).toBeNull();
    expect(suggestionKeyword("가".repeat(51))).toBeNull();
    expect(suggestionKeyword("가".repeat(50))).toBe("가".repeat(50));
  });
});
