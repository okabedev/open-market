import { describe, expect, it } from "vitest";
import { fanOut } from "./fan-out";

describe("fanOut", () => {
  it("서비스마다 동시에 묻고 응답한 서비스와 실패한 서비스를 나눈다", async () => {
    const result = await fanOut(["member", "order", "payment"] as const, async (service) => {
      if (service === "order") {
        throw new Error("connection refused");
      }
      if (service === "payment") {
        return null;
      }
      return [service];
    });
    expect(result).toEqual({
      results: [{ service: "member", value: ["member"] }],
      failed: ["order", "payment"],
    });
  });

  it("제한 시간 안에 답하지 않는 서비스는 실패로 보고 기다리지 않는다", async () => {
    const started = Date.now();
    const result = await fanOut(
      ["member", "review"] as const,
      // review는 signal을 쓰지 않고 끝나지 않는다.
      (service) => (service === "member" ? Promise.resolve(1) : new Promise<number>(() => {})),
      50,
    );
    expect(result).toEqual({ results: [{ service: "member", value: 1 }], failed: ["review"] });
    expect(Date.now() - started).toBeLessThan(1_000);
  });
});
