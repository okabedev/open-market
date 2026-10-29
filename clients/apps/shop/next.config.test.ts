import { describe, expect, it } from "vitest";
import nextConfig from "./next.config";

describe("next.config 보안 헤더", () => {
  it("모든 경로에 프레임 금지, 형식 추측 금지, 리퍼러 정책을 붙인다", async () => {
    const rules = await nextConfig.headers!();

    const all = rules.find((rule) => rule.source === "/:path*");
    expect(all?.headers).toEqual(
      expect.arrayContaining([
        { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
        { key: "X-Frame-Options", value: "DENY" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      ]),
    );
  });
});
