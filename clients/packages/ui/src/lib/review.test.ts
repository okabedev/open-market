import { describe, expect, it } from "vitest";
import { stars } from "./review";

describe("stars", () => {
  it("별점을 다섯 칸 별로 그린다", () => {
    expect(stars(4)).toBe("★★★★☆");
    expect(stars(5)).toBe("★★★★★");
    expect(stars(1)).toBe("★☆☆☆☆");
  });
});
