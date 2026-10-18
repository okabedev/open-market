# 0015. 프론트 화면 스택: Tailwind CSS, shadcn/ui 방식 컴포넌트, Server Action 폼
- 상태: 승인
- 날짜: 2026-10-18

## 맥락
[ADR 0012](0012-frontend-toolchain.md)에서 스타일링, 컴포넌트 라이브러리, 상태 관리, 폼, 컴포넌트 테스트를 M1로 미뤘다. M1에서 shop·seller·admin 첫 화면(가입, 내 정보, 배송지, 입점 신청·심사, 탈퇴)을 만들면서 정해야 한다. 조건은 다음과 같다.
- 앱 3개가 같은 모양을 쓴다. 공유 컴포넌트는 `packages/ui`에 둔다.
- 데이터 읽기는 서버 컴포넌트, 변경은 Server Action이다. 브라우저는 gateway를 부르지 않고 토큰을 갖지 않는다([ADR 0006](0006-nextjs-bff.md)).
- 대화상자, 라벨 같은 상호작용은 접근성을 지켜야 한다.

## 결정 (사용자 결정, 2026-10-18)
| 항목 | 결정 |
|---|---|
| 스타일링 | Tailwind CSS v4. 테마 토큰은 `packages/ui/src/theme.css`(`@theme inline`) 한 곳에 두고 앱의 `globals.css`가 불러온다. 색은 의미 이름(primary, destructive …)으로만 쓴다 |
| 컴포넌트 | shadcn/ui 방식. 컴포넌트 코드를 `packages/ui`에 직접 두고, 접근성이 필요한 프리미티브만 Radix(`react-slot`, `react-label`, `react-alert-dialog`)를 쓴다. 변형은 `class-variance-authority`, 클래스 합치기는 `clsx` + `tailwind-merge`(`cn`) |
| 상태 관리 | 라이브러리를 쓰지 않는다. 서버 상태는 서버 컴포넌트가 읽고, 화면 조건은 URL 검색 조건(`?status=&page=`)에 둔다 |
| 폼 | React 19 `useActionState` + Server Action. 검증은 Server Action 안에서 zod로 한다. 결과는 `packages/ui`의 `FormState`(문구, 필드 오류, 다시 채울 값)로 돌려준다. 서비스 오류(ProblemDetail의 `detail`, `errors`)도 같은 모양으로 바꾼다(`problemState`) |
| 컴포넌트 테스트 | Vitest + Testing Library(jsdom). 기본 환경은 node이고 컴포넌트 테스트 파일만 `// @vitest-environment jsdom`을 적는다. Server Action은 `@/lib/bff`를 가짜로 바꿔 함수로 테스트한다 |
| Playwright E2E | 마일스톤 완료 기준 시나리오(`clients/e2e`)를 로컬 스크립트 `infra/e2e/run.sh`로만 돌린다. CI에서는 돌리지 않는다(PR 검사가 수십 분 늘어난다) |

버전은 ADR 0012와 같은 기준(배포된 지 2주가 지난 안정 버전 중 최신, 2026-10-04 이전)으로 `clients/pnpm-workspace.yaml`의 `catalog`에 적는다. Radix 1.5·2.1.17 등 그 뒤에 나온 버전은 쓰지 않는다.

규칙
- 중복되면 안 되는 추가(배송지, 입점 신청)는 화면을 그릴 때 만든 멱등성 키를 숨은 입력으로 넣고 Server Action이 `Idempotency-Key`로 보낸다. 같은 화면에서 두 번 제출해도 하나만 생긴다.
- 되돌릴 수 없는 작업(삭제, 탈퇴, 승인, 정지 해제)은 `ConfirmSubmit`으로 한 번 더 묻는다. 확인 대화상자라서 JS가 있어야 한다.
- 비밀번호와 계좌 번호는 실패한 폼에 다시 채우지 않는다.
- BFF는 브라우저 주소(`X-Forwarded-For`의 마지막 값)를 gateway에 넘긴다. 가입처럼 로그인 전에 부르는 공개 API는 `bff.publicClient`를 쓴다.

## 결과
- 좋아지는 점: 앱 3개가 토큰 하나로 같은 모양을 쓴다. 컴포넌트 코드를 직접 가져서 라이브러리 업데이트에 끌려가지 않는다. 폼이 JS 없이도 제출되고(확인 대화상자 제외), 클라이언트 번들에 폼 라이브러리가 없다.
- 감수하는 점: 입력 중 즉시 검증이 없다(제출해야 오류가 보인다). 입력이 많은 폼(M2 상품 등록)에서 불편하면 그때 react-hook-form을 다시 검토한다. 컴포넌트 코드를 직접 관리해야 한다.
- 후속 작업: 다크 모드, 상품 목록 같은 큰 화면의 로딩 상태(`loading.tsx`)는 화면이 생길 때 더한다.

## 검토한 대안
- **CSS Modules**: 추가 도구가 없지만 앱 사이 토큰과 변형을 직접 관리해야 한다.
- **Mantine 같은 완성형 라이브러리**: 빨리 만들 수 있지만 런타임과 스타일 체계가 따라오고 서버 컴포넌트와 맞추기 번거롭다.
- **react-hook-form + zod**: 즉시 검증이 되지만 모든 폼이 클라이언트 컴포넌트가 되고, 서버 검증은 어차피 따로 해야 한다.
