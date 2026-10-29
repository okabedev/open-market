# 0012. 프론트 도구와 버전
- 상태: 승인
- 날짜: 2026-10-18

## 맥락
[ADR 0006](0006-nextjs-bff.md)에서 Next.js(App Router) 앱 3개와 pnpm 모노레포를 정했다. M0-e에서 모노레포 골격을 만들려면 버전, lint·포매터, 단위 테스트 도구를 정해야 한다. 스타일링, 컴포넌트 라이브러리, 상태 관리, 폼, BFF 세션 라이브러리는 첫 화면을 만드는 M1에서 정한다(사용자 결정).

버전은 백엔드 이미지와 같은 기준으로 고른다. 배포된 지 2주가 지난 안정 버전 중 가장 최신을 쓴다(2026-10-18 기준, 2026-10-04 이전 배포). 호환이 맞지 않으면 더 낮춘다.

## 결정
| 항목 | 버전 | 근거 |
|---|---|---|
| Node.js | 24 LTS(`.nvmrc`) | 개발 환경 기준([dev-setup.md](../dev-setup.md)) |
| pnpm | 12.9.1 | `clients/package.json`의 `packageManager`. corepack이 이 버전을 받는다 |
| Next.js | 16.4.1 | Turbopack 빌드가 기본. 2026-10-29 16.3.8에서 올렸다(16.4.0 이하의 원격 코드 실행 GHSA-p3m5-59mp-jv5r 등, `pnpm audit`). 16.4.2부터는 2주가 지나지 않았다 |
| React, React DOM | 19.3.0 | 19.3.1은 2주가 지나지 않았다 |
| TypeScript | 6.0.3 | 7.0.2가 있지만 typescript-eslint 8.71이 `typescript <6.1.0`만 지원한다 |
| `@types/node` | 24.19.1 | 런타임(Node 24)과 같은 메이저 |
| ESLint | 9.39.5 | 아래 "ESLint 9" |
| eslint-config-next | 16.4.1 | Next.js와 같은 버전. Core Web Vitals와 TypeScript 규칙 |
| eslint-config-prettier | 10.1.8 | 포맷과 겹치는 lint 규칙을 끈다 |
| Prettier | 3.9.9 | 포매터 |
| Vitest | 5.0.3 (+ vite 8.3.4) | 단위 테스트. vite는 Vitest 5의 피어 의존성이다. vite는 2026-10-29 8.3.2에서 올렸다(8.3.2 이하 취약점, `pnpm audit`) |
| openapi-typescript | 7.13.0 | OpenAPI 문서 → TypeScript 타입(`packages/api-client`). 피어는 `typescript ^5.x`지만 6.0.3에서 생성 결과를 확인했다 |
| openapi-fetch | 0.17.0 | 생성한 경로 타입으로 부르는 fetch 클라이언트 |
| server-only | 0.0.1 | 서버 전용 모듈을 클라이언트 컴포넌트에서 불러오면 빌드를 실패시킨다 |
| `@tosspayments/tosspayments-sdk` | 2.9.0 | 결제위젯 SDK v2의 로더와 타입(ADR 0018, M3에 추가). 2.10.0은 2주가 지나지 않았다. 설치 스크립트와 의존성이 없다 |

설정 위치
- 외부 패키지 버전은 `clients/pnpm-workspace.yaml`의 `catalog`에만 적는다. 각 `package.json`은 `catalog:`로 참조한다.
- TypeScript, ESLint, Prettier 설정은 `packages/config` 한 곳에 두고, 앱과 패키지는 이를 확장한다.
- 의존성 설치 스크립트는 `pnpm-workspace.yaml`의 `allowBuilds`로 허용한 것만 실행한다(pnpm 기본 `strictDepBuilds`). `unrs-resolver`는 네이티브 바인딩이 없을 때만 쓰는 대체 설치라 막는다.
- Next.js 16은 `next build`에서 lint를 돌리지 않는다. lint는 `eslint` CLI로 따로 돌린다.

ESLint 9
- ESLint 9는 지원이 끝났다(npm에서 deprecated). 하지만 10은 쓸 수 없다. eslint-config-next(16.3.8, 16.4.1, 16.4.2 모두)가 쓰는 eslint-plugin-react 7.37.5가 ESLint 10에서 없어진 `context.getFilename()`을 불러 규칙을 불러오지 못한다(`Error while loading rule 'react/display-name'`). eslint-plugin-import 2.32.0, eslint-plugin-jsx-a11y 6.10.2도 피어 범위가 9까지다.
- lint는 개발·CI 도구라서 런타임에는 영향이 없다. eslint-config-next가 ESLint 10에서 동작하는 플러그인으로 바뀌면 함께 올린다.

## 결과
- 좋아지는 점: 버전이 한 파일에 모여 앱 사이에 어긋나지 않는다. Next.js 공식 lint 규칙을 그대로 쓴다.
- 감수하는 점: 지원이 끝난 ESLint 9를 쓴다. TypeScript 7의 빠른 타입 검사를 아직 쓰지 못한다.
- 후속 작업: ESLint 10, TypeScript 7은 typescript-eslint와 eslint-config-next가 지원하면 올린다. 컴포넌트 테스트(Testing Library)와 Playwright E2E는 화면이 생기는 M1에서 정한다. 스타일링·컴포넌트·상태 관리·폼·컴포넌트 테스트는 [ADR 0015](0015-frontend-ui-stack.md), BFF 세션은 [ADR 0014](0014-bff-session.md)로 정했다.

## 검토한 대안
- Biome(lint와 포맷을 하나로): 빠르지만 Next.js 전용 규칙(Core Web Vitals)을 대신하지 못한다. 사용자 결정으로 ESLint + Prettier를 쓴다.
- Jest: Next.js 앱에서 TypeScript·ESM 변환 설정이 더 필요하다. Vitest는 설정 없이 TypeScript를 실행한다.
- 타입 생성 없이 손으로 쓴 fetch 래퍼: 백엔드 API가 바뀌어도 프론트 컴파일이 깨지지 않아 어긋남을 늦게 안다.
- 앱마다 버전 직접 적기: 앱 3개와 패키지가 같은 버전을 쓰는지 사람이 맞춰야 한다.
