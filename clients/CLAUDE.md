# clients (프론트엔드)

pnpm 모노레포. 결정 근거는 [ADR 0006](../docs/adr/0006-nextjs-bff.md), 인증 흐름은 [auth.md](../docs/architecture/auth.md)에 있다.

## 구조
```
clients/
├─ apps/
│  ├─ shop     구매자 (3000)
│  ├─ seller   판매자 (3001)
│  └─ admin    관리자 (3002)
└─ packages/
   ├─ ui          공유 컴포넌트
   ├─ api-client  서비스 OpenAPI에서 생성한 타입과 서버 전용 fetch 래퍼
   └─ config      TypeScript, lint, 포매터 공통 설정
```

## 꼭 지킬 것
- Next.js App Router를 쓴다. 기본은 서버 컴포넌트이고, 상호작용이 필요한 곳만 클라이언트 컴포넌트로 만든다.
- **브라우저 코드는 gateway를 직접 부르지 않는다.** 데이터 읽기는 서버 컴포넌트에서, 변경은 Server Action이나 Route Handler에서 한다.
- 토큰은 서버 세션(Redis)에만 둔다. 브라우저에는 HttpOnly 세션 쿠키 `om_sid`만 간다. 토큰을 props나 클라이언트 상태로 넘기지 않는다.
- `packages/api-client`는 서버 전용이다(`server-only`). 클라이언트 컴포넌트에서 import하지 않는다.
- API 타입은 손으로 쓰지 않고 OpenAPI에서 생성한다. 백엔드 API가 바뀌면 다시 생성한다.
- 상태 변경 요청 중 중복되면 안 되는 것(주문, 결제 승인, 쿠폰 발급, 클레임)은 BFF가 `Idempotency-Key`를 붙인다.
- 파일 업로드는 BFF Route Handler를 거쳐 file 서비스로 보낸다.
- 화면 문구는 한국어다.

## 미정 (M0-e·M1에서 확정)
- 스타일링 방식, 컴포넌트 라이브러리, 상태 관리, 폼 라이브러리
- BFF 세션·OAuth2 클라이언트 구현 라이브러리
- lint·포매터 도구, 테스트 도구(단위, Playwright E2E)
- 명령
