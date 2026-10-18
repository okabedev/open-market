# clients (프론트엔드)

pnpm 모노레포. 결정 근거는 [ADR 0006](../docs/adr/0006-nextjs-bff.md), 도구와 버전은 [ADR 0012](../docs/adr/0012-frontend-toolchain.md), 화면 스택(Tailwind, 컴포넌트, 폼)은 [ADR 0015](../docs/adr/0015-frontend-ui-stack.md), 인증 흐름은 [auth.md](../docs/architecture/auth.md)에 있다.

## 구조
```
clients/
├─ apps/
│  ├─ shop     구매자 (3000)
│  ├─ seller   판매자 (3001)
│  └─ admin    관리자 (3002)
├─ e2e/        마일스톤 E2E 시나리오(Playwright, tests/m<n>-*.spec.ts). infra/e2e/run.sh가 띄운 스택에서 돈다
└─ packages/
   ├─ ui          공유 컴포넌트(shadcn/ui 방식, Radix)와 테마(`theme.css`), 폼 결과(`FormState`). TypeScript 원본을 내보내고 앱이 `transpilePackages`로 컴파일한다
   ├─ api-client  서비스 OpenAPI 문서(openapi/<서비스>.json, 백엔드 테스트가 쓴다)에서 생성한 타입(src/generated)과 서버 전용 클라이언트 `createServiceClient`
   ├─ bff         BFF 세션과 OAuth2 로그인(openid-client + Redis, ADR 0014). 앱은 `@openmarket/bff/next`의 `createNextBff`를 쓴다
   └─ config      TypeScript(tsconfig), ESLint, Prettier 공통 설정
```
- 앱마다 `app/api/health`(Route Handler)가 `{"status":"UP","app":"<앱>"}`을 돌려준다.
- 외부 패키지 버전은 `pnpm-workspace.yaml`의 `catalog`에만 적고 `package.json`은 `catalog:`를 쓴다. 워크스페이스 패키지는 `workspace:*`로 참조한다.

## 꼭 지킬 것
- Next.js App Router를 쓴다. 기본은 서버 컴포넌트이고, 상호작용이 필요한 곳만 클라이언트 컴포넌트로 만든다.
- **브라우저 코드는 gateway를 직접 부르지 않는다.** 데이터 읽기는 서버 컴포넌트에서, 변경은 Server Action이나 Route Handler에서 한다.
- 토큰은 서버 세션(Redis)에만 둔다. 브라우저에는 HttpOnly 세션 쿠키 `om_sid_<앱>`만 간다. 토큰을 props나 클라이언트 상태로 넘기지 않는다.
- 세션은 앱의 `lib/bff.ts`(`createNextBff("<앱>")`)로 쓴다. 로그인 사용자는 `bff.getSession()`(역할만, 토큰 없음), 사용자 API는 `await bff.serviceClient("<서비스>")`. 로그인·콜백·로그아웃은 `app/auth/{login,callback,logout}/route.ts`가 `bff.handlers`를 내보낸다. 로그아웃은 같은 Origin의 폼 POST(`/auth/logout`)로 한다.
- 비밀 값(`<APP>_CLIENT_SECRET`)과 `REDIS_URL` 등은 레포 루트 `.env`에서 읽는다. 앱 디렉터리에 `.env`를 따로 두지 않는다.
- `packages/api-client`는 서버 전용이다(`server-only`). 클라이언트 컴포넌트에서 import하지 않는다.
- API 타입은 손으로 쓰지 않고 OpenAPI에서 생성한다. `openapi/*.json`과 `src/generated`는 고치지 않는다. 백엔드 API가 바뀌면 백엔드 테스트로 문서를 고친 뒤 `generate`로 다시 만든다([conventions.md](../docs/architecture/conventions.md) "REST API").
- 서비스 호출은 로그인 사용자면 `bff.serviceClient("<서비스>")`, 공개 경로만 부르면 `createServiceClient("<서비스>")`를 쓴다. gateway 주소는 서버 환경 변수 `GATEWAY_URL`(기본 `http://localhost:8080`)이다.
- 상태 변경 요청 중 중복되면 안 되는 것(주문, 결제 승인, 쿠폰 발급, 클레임)은 BFF가 `Idempotency-Key`를 붙인다.
- 파일 업로드는 BFF Route Handler를 거쳐 file 서비스로 보낸다.
- 화면 문구는 한국어다.

## 화면 (ADR 0015)
- 스타일은 Tailwind 클래스로 쓴다. 색·반경은 `packages/ui/src/theme.css`의 토큰 이름(`bg-primary`, `text-muted-foreground` …)만 쓰고 색 값을 직접 쓰지 않는다. 앱 `globals.css`는 `tailwindcss`와 `@openmarket/ui/theme.css`만 불러온다.
- 공유 컴포넌트(`Button`, `TextField`, `Card`, `Table`, `Alert`, `ConfirmSubmit`, `AppShell` …)는 `@openmarket/ui`에서 가져온다. 새 컴포넌트가 두 앱 이상에서 쓰이면 `packages/ui`로 옮긴다.
- 전역 상태 라이브러리를 쓰지 않는다. 목록 조건(상태, 쪽)은 URL 검색 조건에 둔다.
- 폼은 `useActionState` + Server Action(`actions.ts`, `"use server"`)이다. 검증은 Server Action 안의 zod 스키마로 하고 `FormState`를 돌려준다. 서비스 오류는 `problemState(error, response.status, values)`로 바꾼다. 비밀번호·계좌 번호는 `values`에 넣지 않는다.
- 로그인이 필요한 화면은 `requireSession("<현재 경로>")`(`lib/session.ts`)로 시작한다.
- 공개 API(가입 등)는 `bff.publicClient("<서비스>")`, 사용자 API는 `bff.serviceClient("<서비스>")`로 부른다. 둘 다 브라우저 주소를 `X-Forwarded-For`로 넘긴다.
- 처리한 행이 목록에서 빠지는 관리자 작업은 성공하면 같은 목록으로 돌아가 `?notice=`로 결과를 보여 준다(`apps/admin/lib/review.ts`).

## 도구
- lint: ESLint 9(flat config, `eslint-config-next`의 Core Web Vitals·TypeScript 규칙 + `eslint-config-prettier`). 포매터: Prettier(마크다운은 제외)
- 단위 테스트: Vitest. 테스트 파일은 대상 옆에 `*.test.ts(x)`로 둔다. Redis가 필요한 테스트(`packages/bff`)는 Testcontainers로 띄우므로 Docker가 있어야 한다
- 컴포넌트 테스트: Testing Library. 앱은 기본 환경이 node라서 파일 맨 위에 `// @vitest-environment jsdom`을 적고 `@testing-library/jest-dom/vitest`를 불러온다(`packages/ui`는 jsdom이 기본). Server Action은 `vi.mock("@/lib/bff")`로 서비스 호출을 바꿔 함수로 테스트한다
- E2E: Playwright(`e2e`). 마일스톤 완료 기준 시나리오를 `tests/m<n>-*.spec.ts`로 둔다. 로컬에서 `infra/e2e/run.sh m<n>`으로만 돌리고 CI에서는 돌리지 않는다(2026-10-18 결정). `pnpm test`에 들어가지 않는다. 화면으로 볼 수 없는 확인(API 403/200)은 `support/oauth.ts`로 토큰을 받아 HTTP로 한다
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
| `pnpm --filter @openmarket/api-client generate` / `generate:check` | OpenAPI 문서에서 타입 다시 만들기 / 최신인지 검사 |
| `../infra/e2e/run.sh m1` | 마일스톤 E2E(레포 루트에서 `infra/e2e/run.sh m1`). [dev-setup.md](../docs/dev-setup.md) "E2E" |
| `pnpm --filter @openmarket/<앱> dev` | 앱 하나를 개발 모드로 띄운다(shop 3000, seller 3001, admin 3002) |

