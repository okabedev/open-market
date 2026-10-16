# 0006. Next.js BFF와 앱 3개
- 상태: 승인
- 날짜: 2026-10-16

## 맥락
브라우저에 토큰을 두면 XSS로 탈취될 수 있다. 구매자, 판매자, 관리자는 화면과 권한, 배포 주기가 다르다.

## 결정
- Next.js(App Router, TypeScript)로 앱 3개(shop, seller, admin)를 만든다. pnpm 모노레포로 묶고 UI, API 클라이언트, 설정을 패키지로 공유한다.
- 각 앱의 Next.js 서버가 BFF다. OAuth2 기밀 클라이언트로 토큰을 받아 Redis 세션에 두고, 브라우저에는 HttpOnly 세션 쿠키만 준다.
- 브라우저 코드는 gateway를 직접 부르지 않는다. 서버 컴포넌트, Server Action, Route Handler만 gateway를 부른다.
- API 타입은 각 서비스의 OpenAPI 문서에서 생성한다.

## 결과
- 좋아지는 점: 토큰이 브라우저에 없다. 앱별로 권한과 번들을 나눈다. 백엔드 API 변경이 타입 오류로 드러난다.
- 감수하는 점: BFF 세션 저장소(Redis)와 토큰 갱신 경쟁을 처리해야 한다([risks.md](../architecture/risks.md) R8).

## 검토한 대안
- 단일 앱 + 역할별 라우트: 공유는 쉽지만 관리자 코드가 구매자 번들과 같은 배포 단위가 된다.
- SPA + 브라우저 토큰: 구현은 단순하지만 보안 기준(NFR-6)에 맞지 않는다.
