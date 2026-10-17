# clients (프론트엔드)

pnpm 모노레포. 결정 근거는 [ADR 0006](../docs/adr/0006-nextjs-bff.md), 도구와 버전은 [ADR 0012](../docs/adr/0012-frontend-toolchain.md), 인증 흐름은 [auth.md](../docs/architecture/auth.md)에 있다.

## 구조
```
clients/
├─ apps/
│  ├─ shop     구매자 (3000)
│  ├─ seller   판매자 (3001)
│  └─ admin    관리자 (3002)
└─ packages/
   ├─ ui          공유 컴포넌트. TypeScript 원본을 내보내고 앱이 `transpilePackages`로 컴파일한다
   ├─ api-client  서비스 OpenAPI에서 생성한 타입과 서버 전용 fetch 래퍼 (M0-e에서 추가)
   └─ config      TypeScript(tsconfig), ESLint, Prettier 공통 설정
```
- 앱마다 `app/api/health`(Route Handler)가 `{"status":"UP","app":"<앱>"}`을 돌려준다.
- 외부 패키지 버전은 `pnpm-workspace.yaml`의 `catalog`에만 적고 `package.json`은 `catalog:`를 쓴다. 워크스페이스 패키지는 `workspace:*`로 참조한다.

## 꼭 지킬 것
- Next.js App Router를 쓴다. 기본은 서버 컴포넌트이고, 상호작용이 필요한 곳만 클라이언트 컴포넌트로 만든다.
- **브라우저 코드는 gateway를 직접 부르지 않는다.** 데이터 읽기는 서버 컴포넌트에서, 변경은 Server Action이나 Route Handler에서 한다.
- 토큰은 서버 세션(Redis)에만 둔다. 브라우저에는 HttpOnly 세션 쿠키 `om_sid`만 간다. 토큰을 props나 클라이언트 상태로 넘기지 않는다.
- `packages/api-client`는 서버 전용이다(`server-only`). 클라이언트 컴포넌트에서 import하지 않는다.
- API 타입은 손으로 쓰지 않고 OpenAPI에서 생성한다. 백엔드 API가 바뀌면 다시 생성한다.
- 상태 변경 요청 중 중복되면 안 되는 것(주문, 결제 승인, 쿠폰 발급, 클레임)은 BFF가 `Idempotency-Key`를 붙인다.
- 파일 업로드는 BFF Route Handler를 거쳐 file 서비스로 보낸다.
- 화면 문구는 한국어다.

## 도구
- lint: ESLint 9(flat config, `eslint-config-next`의 Core Web Vitals·TypeScript 규칙 + `eslint-config-prettier`). 포매터: Prettier(마크다운은 제외)
- 단위 테스트: Vitest. 테스트 파일은 대상 옆에 `*.test.ts(x)`로 둔다
- 의존성 설치 스크립트는 `pnpm-workspace.yaml`의 `allowBuilds`에 적은 것만 돈다. 새 의존성 때문에 설치가 `ERR_PNPM_IGNORED_BUILDS`로 실패하면 스크립트가 필요한지 보고 `true`나 `false`로 적는다

## 명령
`clients/`에서 실행한다. pnpm은 `packageManager`에 고정한 버전을 corepack이 받는다(`corepack enable` 한 번, 또는 `corepack pnpm ...`).
| 명령 | 용도 |
|---|---|
| `pnpm install` | 의존성 설치 |
| `pnpm lint` | 모든 앱·패키지 lint |
| `pnpm typecheck` | 타입 검사. 앱은 `next typegen`으로 라우트 타입을 만든 뒤 `tsc` |
| `pnpm test` | 단위 테스트(Vitest) |
| `pnpm build` | 앱 프로덕션 빌드 |
| `pnpm format` / `pnpm format:check` | Prettier로 고치기 / 검사 |
| `pnpm --filter @openmarket/<앱> dev` | 앱 하나를 개발 모드로 띄운다(shop 3000, seller 3001, admin 3002) |

## 미정 (M1에서 확정)
- 스타일링 방식, 컴포넌트 라이브러리, 상태 관리, 폼 라이브러리
- BFF 세션·OAuth2 클라이언트 구현 라이브러리
- 컴포넌트 테스트, Playwright E2E
