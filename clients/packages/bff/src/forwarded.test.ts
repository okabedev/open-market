import { describe, expect, it } from "vitest";
import { clientAddress } from "./forwarded";

describe("clientAddress", () => {
  it("Next.js 서버가 붙인 마지막 주소를 쓴다. 브라우저가 앞에 넣은 값은 믿지 않는다", () => {
    expect(clientAddress("198.51.100.1, 203.0.113.7")).toBe("203.0.113.7");
    expect(clientAddress("203.0.113.7")).toBe("203.0.113.7");
  });

  it("헤더가 없거나 비었으면 주소가 없다", () => {
    expect(clientAddress(null)).toBeUndefined();
    expect(clientAddress(" , ")).toBeUndefined();
  });
});
