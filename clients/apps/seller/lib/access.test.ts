import { describe, expect, it } from "vitest";
import { isSeller } from "./access";

describe("isSeller", () => {
  it("SELLER 역할이 있어야 판매자 센터를 쓴다", () => {
    expect(isSeller(["BUYER", "SELLER"])).toBe(true);
    expect(isSeller(["BUYER"])).toBe(false);
    expect(isSeller(undefined)).toBe(false);
  });
});
