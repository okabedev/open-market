import { defineConfig } from "eslint/config";
import base from "@openmarket/config/eslint";

export default defineConfig([
  ...base,
  // 앱의 pages 디렉터리를 찾는 규칙이다. 라이브러리에는 pages가 없다.
  { rules: { "@next/next/no-html-link-for-pages": "off" } },
]);
