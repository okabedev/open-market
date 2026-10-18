import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // 컴포넌트 테스트(Testing Library)는 브라우저 DOM이 필요하다(ADR 0015).
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
  },
});
