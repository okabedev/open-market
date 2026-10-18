import { defineConfig, devices } from "@playwright/test";

/**
 * 마일스톤 완료 기준 E2E(docs/roadmap.md). Compose 스택과 앱 3개가 떠 있어야 한다. 보통은 `infra/e2e/run.sh <마일스톤>`이 띄우고
 * 정리한다(docs/dev-setup.md "E2E"). 시나리오는 상태를 쌓으므로 순서대로 한 번만 돈다.
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    ...devices["Desktop Chrome"],
    locale: "ko-KR",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
