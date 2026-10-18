import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // Redis 컨테이너(Testcontainers)를 띄우는 테스트가 있다.
    hookTimeout: 120_000,
    testTimeout: 30_000,
  },
});
