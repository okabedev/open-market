# 0011. Gateway는 Spring Cloud Gateway WebFlux로 만든다
- 상태: 승인
- 날짜: 2026-10-16

## 맥락
Spring Cloud Gateway 5.0(Spring Cloud 2025.1)에는 구현이 두 가지 있다. Reactor Netty 위의 WebFlux 구현(`spring-cloud-starter-gateway-server-webflux`)과 서블릿 위의 WebMVC 구현(`spring-cloud-starter-gateway-server-webmvc`)이다. 다른 서비스는 모두 WebMVC와 가상 스레드를 쓴다.

gateway가 해야 할 일은 다음과 같다([auth.md](../architecture/auth.md), [overview.md](../architecture/overview.md)).
- `/api/<service>/**` → `lb://<service>` 라우팅과 접두어 제거
- JWT 검증(서명, `iss`, `aud`, `exp`), 경로 접두어별 역할 검사
- 사용자·IP 단위 레이트 리밋(NFR-5). 저장소는 Redis
- 바깥에서 들어온 `X-Internal-*` 헤더 제거, 요청 ID

5.0.3 문서를 비교한 결과(R4)는 다음과 같다.

| 항목 | WebFlux | WebMVC |
|---|---|---|
| 레이트 리밋 | `RequestRateLimiter` + `RedisRateLimiter`(Redis Lua 토큰 버킷). 리미터와 키 리졸버를 설정 파일에서 빈으로 지정할 수 있다 | `RateLimiter` 필터는 Bucket4j 기반. 키 리졸버는 Java DSL로만 설정할 수 있고, Redis를 쓰려면 Bucket4j Redis 모듈과 프록시 매니저를 따로 구성한다 |
| 라우트 설정 | 설정 파일(`spring.cloud.gateway.server.webflux.routes`)과 Java DSL | 설정 파일과 Java DSL |
| JWT 리소스 서버 | Spring Security WebFlux(`ServerHttpSecurity`) | Spring Security 서블릿 |
| 실행 모델 | 이벤트 루프. 블로킹 호출을 하면 안 된다 | 요청당 스레드(가상 스레드 가능) |

## 결정
- **WebFlux 구현을 쓴다.**
- 라우트, 공개 경로, 레이트 리밋 한도, 토큰 검증 설정은 config-repo의 `gateway.yml`에 둔다. 코드에는 정책의 틀만 둔다: 역할 경로 규칙, 내부 헤더 제거, 키 리졸버, 요청 ID.
- 인가 규칙(먼저 맞는 것이 이긴다)
  1. 서비스의 `/internal/**`, `/actuator/**`, `/v3/api-docs/**`는 막는다(`denyAll`). OpenAPI 문서는 서비스 테스트가 직접 읽는다(M6 보안 점검에서 더했다).
  2. `/api/*/v1/seller/**`는 `SELLER`, `/api/*/v1/admin/**`는 `ADMIN`.
  3. `openmarket.gateway.public-paths`에 적은 메서드·경로는 토큰 없이 통과한다.
  4. 나머지는 유효한 토큰이 있어야 한다(기본 거부).
- 토큰은 `aud`가 `shop`, `seller`, `admin` 중 하나여야 한다. 서비스 간 토큰(`aud=internal`)은 gateway를 지나지 않는다. 권한은 `roles` 클레임에 `ROLE_`을 붙여 만든다.
- 레이트 리밋 키는 로그인한 요청이면 `user:<sub>`, 익명이면 `ip:<주소>`다. 주소는 `X-Forwarded-For`의 마지막 값(BFF가 붙인 값)이다. 이 값을 믿는 근거는 gateway에 BFF만 닿는다는 전제다. 운영 배치에서는 gateway를 외부에 직접 열지 않는다. BFF는 앱 서버(`packages/bff/bin/serve.mjs`)가 브라우저가 보낸 `X-Forwarded-*`를 지우고 연결 주소를 넣은 값을 넘긴다. Next.js 기본 서버는 브라우저가 넣은 값을 그대로 두므로 위조로 익명 한도를 피할 수 있었다(M6 보안 점검).
- Redis 장애 시 레이트 리미터는 요청을 통과시킨다(fail-open). 명령 타임아웃을 200ms로 둬서 그 사이 요청이 멈추지 않게 한다.
- 요청 ID는 따로 만들지 않고 trace id를 응답 헤더 `X-Request-Id`로 돌려준다. 서비스에는 `traceparent`로 같은 trace가 넘어간다.
- actuator는 관리 포트(8081)에만 둔다. Prometheus는 Eureka 메타데이터 `management.port`로 그 포트를 찾는다.
- CORS는 열지 않는다. 브라우저는 gateway를 직접 부르지 않고 BFF만 부른다([ADR 0006](0006-nextjs-bff.md)).

## 결과
- 좋아지는 점
  - Redis 레이트 리밋을 추가 라이브러리 없이 쓰고, 한도를 config-repo에서 바꾼다.
  - 프록시는 I/O만 하므로 이벤트 루프 모델에 맞다. 적은 스레드로 많은 동시 연결을 받는다.
- 감수하는 점
  - gateway만 리액티브 스택이다. 보안 설정(`ServerHttpSecurity`), 필터(`WebFilter`, `GlobalFilter`), 테스트 방식이 다른 서비스와 다르다. gateway에는 도메인 로직이 없고 `common-web`을 쓰지 않으므로 영향은 이 모듈 안에 머문다.
  - gateway 코드에서 블로킹 호출을 하면 안 된다.
  - 오류 응답(401, 403, 429)은 본문 없이 상태와 헤더(`WWW-Authenticate`, `X-RateLimit-*`, `X-Request-Id`)만 준다. 서비스의 `ProblemDetail` 형식과 다르다. BFF는 상태 코드로 처리한다.
- 후속 작업
  - 로그인·가입·쿠폰 발급의 엄격한 한도는 해당 기능(M1, M5)과 함께 라우트별 리미터로 더한다. M1: 가입·이메일 인증 라우트에 `strictRateLimiter`를 걸었고([auth.md](../architecture/auth.md) "auth 구현"), 로그인은 브라우저가 auth에 직접 가므로 auth가 건다.
  - 서킷 브레이커와 재시도는 서비스가 늘어나는 M2 이후에 라우트별로 정한다.
- 확인한 것: [risks.md](../architecture/risks.md) R4.

## 검토한 대안
- **WebMVC 구현**: 모든 서비스가 같은 서블릿·가상 스레드 모델을 쓰게 된다. 하지만 레이트 리밋이 Bucket4j뿐이고 키 리졸버를 설정으로 바꿀 수 없다. Redis 분산 버킷도 직접 구성해야 해서 제외했다.
- **gateway에서 CORS 허용**: 브라우저가 gateway를 직접 부르는 구조가 아니므로 필요 없다. 열어 두면 BFF를 우회하는 경로가 생긴다.
- **요청마다 별도 UUID 요청 ID**: trace id와 식별자가 둘이 되어 로그·트레이스를 오갈 때 변환이 필요하다. trace id 하나로 통일했다.
