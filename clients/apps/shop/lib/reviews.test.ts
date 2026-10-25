import { describe, expect, it } from "vitest";
import { ratingLabel, stars } from "./reviews";

describe("stars", () => {
  it("별점을 다섯 칸 별로 그린다", () => {
    expect(stars(4)).toBe("★★★★☆");
    expect(stars(5)).toBe("★★★★★");
    expect(stars(1)).toBe("★☆☆☆☆");
  });
});

describe("ratingLabel", () => {
  it("평균은 소수 첫째 자리, 리뷰 수는 천 단위로 끊는다", () => {
    expect(ratingLabel(4.3, 1234)).toBe("4.3 (리뷰 1,234개)");
    expect(ratingLabel(5, 1)).toBe("5.0 (리뷰 1개)");
  });

  it("리뷰가 없으면 리뷰 없음이다", () => {
    expect(ratingLabel(undefined, 0)).toBe("리뷰 없음");
    expect(ratingLabel(null, undefined)).toBe("리뷰 없음");
  });
});
