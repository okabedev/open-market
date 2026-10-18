# 0014. BFF 세션은 openid-client와 Redis로 직접 만든다
- 상태: 승인
- 날짜: 2026-10-18

## 맥락
[ADR 0006](0006-nextjs-bff.md)에서 Next.js 서버를 BFF로 정했다. BFF는 auth의 기밀 클라이언트로 토큰을 받아 Redis 세션에 두고, 브라우저에는 HttpOnly 쿠키만 준다. 구현 라이브러리는 M1에서 정하기로 했다([ADR 0012](0012-frontend-toolchain.md)).

조건은 다음과 같다.
- Authorization Code + PKCE, OIDC 로그아웃, 리프레시 토큰 rotation(auth는 쓴 리프레시 토큰을 다시 받지 않는다)
- 토큰은 Redis에만 둔다. 서버 컴포넌트, Server Action, Route Handler 어디서든 세션을 읽고 토큰을 갱신한다
- 같은 세션을 여러 요청이나 인스턴스가 동시에 갱신해도 세션이 끊기지 않는다(R8). rotation 때문에 두 번째 갱신은 `invalid_grant`가 된다

후보(2026-10-18)
| 후보 | 판단 |
|---|---|
| openid-client 6.8.8 + redis 6.3.0 | OIDC 프로토콜(discovery, PKCE, 토큰 교환, 갱신, 폐기, 로그아웃 URL)만 맡는다. 세션과 락은 직접 만든다 |
| Auth.js(next-auth) v5 | 5.0.0-beta.32로 아직 정식이 아니다. 갱신과 동시 갱신 처리는 콜백 안에 직접 짜야 하고, Redis 세션은 어댑터를 따로 붙인다 |
| Better Auth 1.7 | 자체 사용자 DB를 가진 인증 서버다. 계정은 auth가 갖는다는 결정([ADR 0005](0005-own-authorization-server.md))과 겹친다 |

## 결정
- **openid-client와 redis로 `clients/packages/bff`를 만든다.** 앱 3개는 `createNextBff("<앱>")` 하나로 쓴다.
- 핵심(`createBff`)은 표준 `Request`·`Response`만 쓰고 Next.js를 모른다. Next.js 연결(`cookies()`, Redis 연결 하나)은 `@openmarket/bff/next`에 둔다.
- 라우트: `GET /auth/login?returnTo=`, `GET /auth/callback`, `POST /auth/logout`.
- 쿠키: 세션 `om_sid_<앱>`(Path `/`), 로그인 진행 `om_login_<앱>`(Path `/auth`, 10분). 둘 다 HttpOnly, SameSite=Lax이고 Secure는 `SESSION_COOKIE_SECURE`로 켠다. 쿠키는 포트를 구분하지 않아서 이름에 앱을 붙인다.
- Redis 키: `bff:<앱>:session:<sid>`(TTL은 리프레시 토큰 수명. shop·seller 14일, admin 8시간), `bff:<앱>:login:<state>`(한 번 꺼내면 지운다), `bff:<앱>:lock:<sid>`(갱신 락 10초).
- 갱신: 액세스 토큰이 30초 안에 만료되면 갱신한다. 같은 프로세스의 동시 요청은 Promise 하나로 묶는다. 프로세스 사이는 Redis 락(`SET NX PX`, 소유자 토큰으로만 해제)을 잡은 한 요청만 auth에 요청하고, 나머지는 새 토큰이 저장될 때까지 최대 5초 기다린다. 갱신이 `invalid_grant`로 거절되면 세션을 지운다.
- 보안: 콜백은 쿠키의 state와 쿼리의 state가 같아야 한다(login CSRF). 로그인할 때마다 새 세션 ID를 만든다(세션 고정). `returnTo`는 앱 안의 경로만 받는다. 로그아웃은 같은 Origin의 POST만 받고, 리프레시 토큰을 폐기한 뒤 auth의 `end_session_endpoint`로 보낸다.
- 설정: 레포 루트 `.env`를 BFF가 처음 쓰일 때 `process.loadEnvFile`로 읽는다. `<APP>_CLIENT_SECRET`(필수), `OIDC_ISSUER`, `APP_URL`, `REDIS_URL`, `SESSION_COOKIE_SECURE`.

## 결과
- 좋아지는 점
  - R8을 코드와 테스트로 다룬다. Testcontainers Redis로, BFF 인스턴스 둘이 동시에 20번 갱신해도 auth에는 한 번만 요청하는 것을 확인한다. 락을 끄면 이 테스트가 실패한다(갱신 2회).
  - 실제 auth와 브라우저로 확인(2026-10-18): 액세스 토큰을 강제로 만료시킨 뒤 서버 컴포넌트 5개, Route Handler 5개, Server Action 1개를 동시에 보냈다. 세 번 반복해 모두 인증됐고, 세션이 유지됐고, 리프레시 토큰은 한 번만 바뀌었다.
- 감수하는 점
  - 세션·쿠키·락 코드를 직접 유지한다. 프로토콜 검증(state, nonce, PKCE, ID 토큰)은 openid-client가 한다.
  - 서버 컴포넌트에서는 쿠키를 지울 수 없다. 세션이 끝나도 쿠키는 남지만, Redis에 세션이 없으므로 로그아웃 상태로 본다.
- 후속 작업: Playwright E2E를 정하면 위 브라우저 확인을 테스트로 옮긴다.

## 검토한 대안
위 후보 표와 같다.
