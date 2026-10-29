import type { IncomingMessage } from "node:http";
import { describe, expect, it } from "vitest";
import { useConnectionAddress } from "../bin/serve.mjs";
import { clientAddress } from "./forwarded";

function request(headers: Record<string, string>, remoteAddress?: string): IncomingMessage {
  return { headers, socket: { remoteAddress } } as unknown as IncomingMessage;
}

describe("useConnectionAddress", () => {
  it("브라우저가 넣은 X-Forwarded-For를 버리고 연결 주소를 쓴다", () => {
    const req = request({ "x-forwarded-for": "198.51.100.1, 203.0.113.7" }, "192.0.2.10");

    useConnectionAddress(req);

    expect(req.headers["x-forwarded-for"]).toBe("192.0.2.10");
    expect(clientAddress(req.headers["x-forwarded-for"] as string)).toBe("192.0.2.10");
  });

  it("다른 X-Forwarded-* 헤더도 지운다", () => {
    const req = request(
      { "x-forwarded-host": "evil.example", "x-forwarded-proto": "https", host: "localhost:3000" },
      "192.0.2.10",
    );

    useConnectionAddress(req);

    expect(req.headers["x-forwarded-host"]).toBeUndefined();
    expect(req.headers["x-forwarded-proto"]).toBeUndefined();
    expect(req.headers.host).toBe("localhost:3000");
  });

  it("연결 주소를 모르면 X-Forwarded-For를 넣지 않는다", () => {
    const req = request({ "x-forwarded-for": "198.51.100.1" });

    useConnectionAddress(req);

    expect(req.headers["x-forwarded-for"]).toBeUndefined();
  });
});
