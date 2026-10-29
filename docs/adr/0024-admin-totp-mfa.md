# 0024. 관리자 TOTP는 Spring Security 7 다중 요소 인증의 요소 권한으로 요구한다
- 상태: 승인
- 날짜: 2026-10-28

## 맥락
`ADMIN` 역할 계정은 비밀번호 다음에 TOTP를 확인해야 로그인이 끝난다([auth.md](../architecture/auth.md) "관리자 2단계 인증"). R15는 비밀번호와 TOTP 사이에 인가 코드가 나가면 안 된다는 위험이다. 폼 로그인 성공 처리, 잠금 횟수, 로그인 레이트 리밋, 인가 요청 저장(돌아갈 주소)이 두 단계에 걸쳐 맞아야 한다. 설계 때 Spring Security 7의 다중 요소 인증 지원을 쓸지 이 PR에서 확인하기로 했다.

확인한 것(Spring Security 7.1.1, Boot 4.1.1):
- 인증 수단마다 요소 권한(`FactorGrantedAuthority`)이 붙는다. 폼 로그인은 `FACTOR_PASSWORD`다. 사용자 정의 요소도 같은 권한으로 만든다(`FactorGrantedAuthority.withAuthority`).
- `AuthorizationManagerFactories.multiFactor().requireFactors(...).when(조건)`으로 조건에 맞는 인증에만 요소를 요구한다. 요소가 없어 거절되면 `defaultDeniedHandlerForMissingAuthority`로 그 요소의 화면에 보내고, 요청을 저장한다.
- 인가 코드를 내는 `OAuth2AuthorizationEndpointFilter`는 권한 검사 필터(`AuthorizationFilter`) **뒤에** 있다(`OAuth2AuthorizationEndpointConfigurer`가 `addFilterAfter(..., AuthorizationFilter.class)`로 넣는다). 그래서 인가 엔드포인트 체인의 권한 규칙이 요소를 요구하면, 비밀번호만 맞힌 세션은 코드를 받기 전에 막힌다. 앞에 있는 요청 검증 필터는 요청 형식만 보고 코드를 내지 않는다.
- TOTP 인증 수단은 없다(비밀번호, 일회용 토큰, WebAuthn, X.509 등만 있다). TOTP 계산, 등록, 확인 화면은 직접 만든다.

## 결정
- 2단계 인증 요소는 `FACTOR_TOTP` 권한이다. 인가 엔드포인트 체인의 규칙을 `multiFactor().requireFactors("FACTOR_TOTP").when(ROLE_ADMIN이 있는 인증)`으로 만든다. 다른 로그인과 클라이언트 인증(토큰 엔드포인트)은 그대로다.
- 요소가 없는 관리자는 `/login/totp`로 보낸다. 인가 요청은 저장되고, 확인하면 그 주소로 돌아간다.
- `/login/totp`는 auth 화면(Thymeleaf)이다. 등록하지 않았으면 비밀 값(QR, 문자열)과 코드 입력, 등록했으면 코드 입력이다. 코드가 맞으면 지금 인증에 `FACTOR_TOTP`를 더하고 세션 ID를 바꾼다.
- TOTP(RFC 6238, HMAC-SHA1, 6자리, 30초, 앞뒤 한 단계)는 도메인에서 계산한다. HMAC은 `common-core`에 둔다(도메인은 `javax.crypto`를 쓰지 않는다). QR은 ZXing core(3.5.4, 2025-11-11 릴리스)로 SVG를 그린다.
- 틀린 코드는 비밀번호 실패와 같은 계정 실패 횟수로 센다. 관리자는 비밀번호가 맞아도 횟수를 지우지 않고 2단계가 맞아야 지운다. 비밀번호 성공으로 지우면 다시 로그인할 때마다 처음부터 세어 코드를 끝없이 추측할 수 있다.
- 2단계 확인(`POST /login/totp`, `/login/totp/enroll`)은 `POST /login`과 같은 IP 카운터로 레이트 리밋을 받는다.
- 비밀 값은 `AUTH_TOTP_SECRET_KEY`(서명 키와 다른 키)로 AES-256-GCM 암호화해 `totp_credential`에 둔다. 복구 코드 10개는 bcrypt 해시만 `totp_recovery_code`에 둔다.

## 결과
- 좋아지는 점
  - "코드를 내기 전에 요소를 본다"를 프레임워크의 권한 검사가 맡는다. 직접 만든 필터로 인가 엔드포인트를 감싸지 않아 필터 순서를 맞출 일이 없다.
  - 2단계가 필요한 조건(ADMIN 역할)이 권한 규칙 한 곳에 있다. 다른 요소(WebAuthn 등)를 더할 때도 같은 방식이다.
- 감수하는 점
  - 요소 권한은 세션에 저장한 인증에 붙는다. 로그인 세션이 남아 있는 동안은 다시 묻지 않는다(리프레시 토큰 갱신도 묻지 않는다, auth.md).
  - E2E와 부하·장애 시험 도구도 관리자 로그인에서 코드를 만든다(`clients/e2e/support/totp.ts`, `infra/k6/lib/totp.js`). 같은 30초 단계의 코드는 다시 받지 않으므로 연달아 로그인하면 다음 단계까지 기다린다.
  - 기존 `.env`에 `AUTH_TOTP_SECRET_KEY`를 더해야 auth가 뜬다.
  - 복구 코드를 다 쓰거나 기기를 잃으면 운영자가 등록을 지운다([런북](../runbook.md) "관리자 TOTP 등록 해제"). 지우면 다음 로그인에 다시 등록한다.

## 검토한 대안
- **비밀번호 성공 처리를 바꿔 인증을 미완성으로 두기**: 관리자면 `isAuthenticated()=false`인 중간 인증을 세션에 두고 TOTP 뒤에 완성한다. 인가 엔드포인트는 "로그인 안 됨"으로 보고 로그인 화면으로 보내므로 2단계 화면으로 보내는 처리, 요청 저장, 다른 체인의 인증 판단을 모두 직접 맞춰야 한다. R15가 말한 실수 지점이 그대로 남는다.
- **인가 코드 요청 검증기에서 거절**: 역할 검사(`RequiredRoleValidator`)처럼 검증기에서 요소를 보면 거절은 `access_denied` 오류 응답이라 2단계 화면으로 보낼 수 없다.
- **TOTP 라이브러리**: 계산은 HMAC 한 번과 자리 자르기라 RFC 시험 벡터로 검증하는 편이 의존성보다 가볍다. QR 생성만 라이브러리를 쓴다.
