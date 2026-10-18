import path from "node:path";
import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname) },
  },
  test: {
    // 기본은 node다. 컴포넌트 테스트는 파일 맨 위에 `// @vitest-environment jsdom`을 적는다(ADR 0015).
    environment: "node",
  },
});
