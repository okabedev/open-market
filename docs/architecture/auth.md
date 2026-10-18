# 인증·인가

## 구성
| 구성 요소 | 역할 |
|---|---|
| auth | Spring Authorization Server. 로그인 화면, 가입 API, 계정·역할, OAuth2 클라이언트 등록, JWT 발급, JWK 공개 |
| Next.js 서버(BFF) | OAuth2 클라이언트. 토큰을 서버 세션(Redis)에 두고, 브라우저에는 세션 쿠키만 준다 |
| gateway | 리소스 서버. JWT 검증, 경로별 역할 검사, 레이트 리밋 |
| 각 서비스 | 리소스 서버. JWT를 다시 검증하고, 리소스 소유권(내 주문, 내 상품)을 검사한다 |

## 로그인 흐름 (Authorization Code + PKCE)
```mermaid
sequenceDiagram
  autonumber
  participant U as 브라우저
  participant N as Next.js 서버(BFF)
  participant A as auth
  participant G as gateway
  U->>N: GET /login
  N-->>U: 302 → auth /oauth2/authorize (state, PKCE challenge)
  U->>A: 로그인 화면, 자격 증명 제출
  A-->>U: 302 → N /auth/callback?code
  U->>N: /auth/callback?code&state
  N->>A: POST /oauth2/token (code, verifier, client secret)
  A-->>N: access token, refresh token, id token
  N-->>N: Redis 세션에 토큰 저장
  N-->>U: Set-Cookie om_sid (HttpOnly, SameSite=Lax)
  U->>N: 페이지 요청·Server Action
  N->>G: Authorization: Bearer access token
```
- 세 앱은 각각 기밀 클라이언트(`shop`, `seller`, `admin`)다. 클라이언트 시크릿은 Next.js 서버 환경 변수에만 둔다.
- 액세스 토큰이 만료되면 BFF가 리프레시 토큰으로 갱신한다. 리프레시 토큰은 쓸 때마다 새로 발급한다(rotation).
- 로그아웃하면 BFF 세션을 지우고, 리프레시 토큰을 폐기(revoke)한 뒤, auth의 OIDC 로그아웃 엔드포인트로 보낸다.
- 가입은 shop의 화면에서 받는다. BFF가 auth 가입 API(`POST /api/auth/v1/accounts`, 공개, 레이트 리밋 적용)를 부른다. 로그인 화면만 auth가 그린다.
- BFF 구현 방식(라이브러리 선택)은 M1에서 정한다.

## 토큰
| 항목 | 값 |
|---|---|
| 형식 | JWT, RS256 |
| 액세스 토큰 수명 | 10분 |
| 리프레시 토큰 수명 | shop·seller 14일, admin 8시간 |
| `sub` | `accountId` (= `memberId` = 판매자면 `sellerId`) |
| `roles` | `BUYER`, `SELLER`, `ADMIN` 중 보유한 것 |
| `aud` | 클라이언트 ID. 서비스 간 토큰은 `internal` |
| `scope` | 사용자 토큰 `openid profile api`, 서비스 간 토큰 `internal` |

- 서명 키는 auth 스키마에 저장한다. 처음 기동할 때 만들고, 회전하면 이전 키를 JWK 세트에 일정 기간 남긴다.
- 역할이 바뀌면(입점 승인, 판매자 정지) 다음 토큰 갱신부터 반영된다. 최대 지연은 액세스 토큰 수명(10분)이다. 판매자 정지는 catalog가 상품을 바로 판매 중지하므로, 그 사이에 판매가 일어나지 않는다.
- admin 클라이언트는 `ADMIN` 역할이 없는 계정의 인가 요청을 거절한다. 관리자 2단계 인증(TOTP)은 M6에서 다룬다.

## 인가
1. **gateway**: 서명, `iss`, `exp`, `aud`(`shop`, `seller`, `admin` 중 하나)를 검증한다. 경로 접두어로 역할을 검사한다. `/v1/seller/**`는 `SELLER`, `/v1/admin/**`는 `ADMIN`이 필요하다. 공개 경로는 config-repo `gateway.yml`의 `openmarket.gateway.public-paths`에 적고, 나머지는 토큰이 있어야 한다. 서비스의 `/internal/**`와 `/actuator/**`는 넘기지 않는다. 바깥에서 들어온 내부 헤더(`X-Internal-*`)는 지운다. 규칙 순서와 근거는 [ADR 0011](../adr/0011-gateway-webflux.md).
2. **서비스**: 같은 검증을 한 번 더 한다. gateway를 우회한 호출도 막기 위해서다. 소유권은 유스케이스에서 검사한다(예: 하위 주문의 `sellerId`가 토큰의 `sub`와 같은지).
3. **서비스 간 호출**: 각 서비스는 자기 이름을 클라이언트 ID로 쓰는 `client_credentials` 토큰을 받는다. 내부 API는 `/internal/v1/**`에 두고 `internal` scope를 요구한다. gateway는 `/internal/**`를 라우팅하지 않는다.

## 보안 기본값
- 비밀번호는 `DelegatingPasswordEncoder`(기본 bcrypt)로 저장한다.
- 로그인 5회 연속 실패 시 15분 잠금(ACC-1). 로그인과 가입은 IP 단위 레이트 리밋을 건다(NFR-5).
  - 가입 API는 gateway를 거치므로 gateway에서 제한한다.
  - 로그인 화면은 브라우저가 auth에 직접 가므로 gateway를 거치지 않는다. auth가 로그인 처리 앞에서 Redis로 IP 단위 제한을 건다.
- CSRF: BFF 세션 쿠키는 `SameSite=Lax`로 둔다. Server Action은 Next.js가 Origin을 검사하고, Route Handler는 직접 Origin을 검사한다. auth 로그인 화면은 Spring Security CSRF 토큰을 쓴다.
- 세션 쿠키는 운영 기준과 같게 `HttpOnly`를 켠다. `Secure`는 로컬 HTTP에서 끄고 설정으로 바꿀 수 있게 한다.

## issuer 호스트 이름
토큰의 `iss`는 브라우저, IDE, 컨테이너 모두에서 같은 주소로 보여야 한다. 근거와 실측은 [ADR 0013](../adr/0013-auth-issuer-host.md)에 있다.
- issuer를 `http://auth.openmarket.local:9000`으로 고정한다. auth는 요청 주소와 상관없이 이 값을 쓴다.
- auth는 컨테이너든 IDE든 호스트 포트 9000에 있다.
- 브라우저와 IDE는 hosts 파일로 `auth.openmarket.local`을 `127.0.0.1`로 보낸다([dev-setup.md](../dev-setup.md) 3절).
- 컨테이너는 `extra_hosts: auth.openmarket.local:host-gateway`로 호스트에 닿는다(gateway, `apps.yaml`).

## auth 구현
코드는 `services/infra/auth`에 있다. 설정은 config-repo `auth.yml`(`openmarket.auth`)이다.
- 클라이언트: `user-clients`(shop, seller, admin)와 `service-clients`(서비스 이름)를 기동할 때마다 DB(`oauth2_registered_client`)에 맞춘다. 행 ID는 클라이언트 ID이고, 시크릿은 바뀌었을 때만 다시 해시한다. 설정에서 빼도 행은 지우지 않는다.
  - 사용자 클라이언트: Authorization Code + PKCE 필수, 동의 화면 없음, 리프레시 토큰 rotation. `required-role`이 있으면 그 역할이 없는 계정의 인가 요청을 `access_denied`로 돌려보낸다(admin은 `ADMIN`).
  - 서비스 클라이언트: `client_credentials`만, scope `internal`. 다른 서비스를 동기 호출하는 서비스만 등록한다.
- 인가·동의: `oauth2_authorization`, `oauth2_authorization_consent`(JDBC). 로그인 세션은 auth 인스턴스의 HTTP 세션이다.
- 서명 키: `signing_key` 표. 처음 기동할 때 RSA 2048 키를 만들고, 개인 키는 `AUTH_SIGNING_KEY_SECRET`(.env)으로 AES-256-GCM 암호화해 저장한다. 서명은 가장 최근의 ACTIVE 키로 하고, 헤더에 `kid`를 넣는다. JWK 세트에는 RETIRED가 아닌 키를 모두 싣는다. 회전 작업은 아직 없다.
- `roles`는 토큰을 만들 때마다 DB에서 읽는다. 그래서 갱신하면 바뀐 역할이 반영된다.
- 관리자 계정은 `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD`(.env)로 기동할 때 한 번 만든다. 이미 있으면 바꾸지 않는다.
- 로그인 화면은 지금 Spring Security 기본 폼이다. 한국어 화면과 잠금은 가입 기능과 함께 만든다.
- actuator는 관리 포트 9001에만 있다.
