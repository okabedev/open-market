import { defineConfig } from "eslint/config";
import base from "@openmarket/config/eslint";

export default defineConfig([
  ...base,
  // 앱의 pages 디렉터리를 찾는 규칙이다. E2E 패키지에는 pages가 없다.
  { rules: { "@next/next/no-html-link-for-pages": "off" } },
  globalIgnoresReports(),
]);

/** Playwright 결과물은 lint하지 않는다. */
function globalIgnoresReports() {
  return { ignores: ["playwright-report/**", "test-results/**"] };
}
