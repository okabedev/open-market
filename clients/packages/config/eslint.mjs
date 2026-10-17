// clients 공통 ESLint 설정(flat config). 앱과 React 패키지가 같은 규칙을 쓴다.
// Next.js 공식 규칙(Core Web Vitals, TypeScript)에 포맷 관련 규칙을 끄는 eslint-config-prettier를 더한다.
import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";
import prettier from "eslint-config-prettier/flat";

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  prettier,
  globalIgnores([".next/**", "out/**", "build/**", "coverage/**", "next-env.d.ts"]),
]);
