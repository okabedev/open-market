---
name: add-service
description: 오픈 마켓에 새 백엔드 서비스(core 또는 infra)를 추가하거나 처음 구현을 시작할 때 사용한다. Gradle 모듈, 패키지 골격, 스키마·계정, config, gateway 라우트, Debezium, 토픽을 빠짐없이 연결한다.
---

# 서비스 추가

서비스 목록은 [overview.md](../../../docs/architecture/overview.md)가 원본이다. 목록에 없는 서비스를 추가하려면 먼저 사용자에게 묻고, ADR과 overview를 고친다.

## 확인
1. 이름이 고유 명사 한 단어이고 `-service` 접미사가 없는지 확인한다.
2. overview에서 포트와 스키마를 확인한다. 새 서비스면 포트 규칙(core 81xx, infra 82xx)에 따라 다음 번호를 쓴다.
3. 이 서비스가 발행·구독하는 이벤트를 [context-map.md](../../../docs/architecture/context-map.md)에서 확인한다.

## 체크리스트
골격은 `services/core/member`를 본뜬다(빌드 파일, `application.yml`, 계층별 `package-info.java`, `ArchitectureTest`, 통합 테스트).
- [ ] `services/settings.gradle.kts`에 `:core:<name>` 또는 `:infra:<name>` 추가. `build-logic`의 서비스 convention 플러그인을 적용한다
- [ ] 패키지 골격 `com.openmarket.<name>`: `command/{application,domain}`, `query/{application,dao,dto}`, `infra`, `ui/{web,event}`
- [ ] `src/main/resources/application.yml`: 앱 이름, config 서버 import와 `fail-fast`, Flyway 위치, springdoc 기본 응답 타입만 둔다
- [ ] 테스트 설정은 `src/test/resources/config/application.yml`에 둔다. 같은 이름(`application.yml`)을 테스트 리소스 루트에 두면 main 설정을 가린다
- [ ] Flyway 위치에 공통 테이블 추가: `spring.flyway.locations=classpath:db/migration,classpath:db/common/web,classpath:db/common/messaging` ([data.md](../../../docs/architecture/data.md)). 서비스 마이그레이션은 `V1`부터
- [ ] ArchUnit 테스트: `common-test`의 `ArchitectureRules`를 이 서비스 패키지에 적용 (사용 예는 그 클래스의 Javadoc)
- [ ] 스키마와 계정: `infra/postgres/init/services.txt`에 이름 추가, `.env.example`과 `infra/compose/compose.yaml`의 postgres 환경 변수에 `<NAME>_DB_PASSWORD` 추가. 이미 뜬 DB에는 `docker compose down -v` 후 다시 띄워 적용한다
- [ ] `infra/kafka/topics.txt`: 이 서비스가 발행하는 토픽과 `.DLT` 토픽 선언(context-map과 같은지 확인). Debezium은 고치지 않는다. `outbox_event` 테이블은 Flyway가 만들 때 Postgres event trigger가 publication에 넣는다
- [ ] `infra/config-repo/<name>.yml`: 포트, DB 비밀번호 자리 표시자 `${<NAME>_DB_PASSWORD}`, Kafka 소비자 그룹 `<name>`. 스키마·계정·URL은 공통 `application.yml`이 서비스 이름으로 정한다
- [ ] gateway 라우트 `/api/<name>/**` → `lb://<name>`, `StripPrefix=2` (외부 API가 있을 때). `infra/config-repo/gateway.yml`의 `routes`에 더하고, 토큰 없이 열 API는 `openmarket.gateway.public-paths`에 적는다
- [ ] admin 앱 관리자 조회 대상 `clients/apps/admin/lib/admin-services.ts`의 `ADMIN_SERVICES`에 `<name>` (gateway 라우트가 있을 때). 감사 로그·DLT 메시지 화면이 이 목록에 묻는다([ADR 0023](../../../docs/adr/0023-admin-ops-queries.md))
- [ ] API(외부 `/v1` 또는 내부 `/internal`)가 있으면 `implementation(project(":libs:common-security"))`를 더한다. 토큰 재검증과 역할 경로 규칙이 붙는다. 테스트는 `spring-boot-starter-security-test`의 `jwt()`로 인증을 넣고, 테스트 설정에 `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` 자리 값을 둔다(예: `core/member`)
- [ ] 외부 API가 있으면 OpenAPI 문서를 프론트에 연결한다: 앱 통합 테스트에 `OpenApiSnapshot.assertMatches("<name>", ...)`를 두고 `OPENAPI_UPDATE=true`로 `clients/packages/api-client/openapi/<name>.json`을 만든다. `api-client`의 `src/client.ts` `ServicePaths`와 `src/index.ts`에 서비스를 더하고 `pnpm --filter @openmarket/api-client generate`
- [ ] auth에 `client_credentials` 클라이언트 `<name>` 등록 (다른 서비스를 동기 호출할 때): `infra/config-repo/auth.yml`의 `openmarket.auth.service-clients`에 `<name>.secret: ${<NAME>_CLIENT_SECRET}`, `.env.example`에 값, `infra/compose/compose.yaml`의 auth 환경 변수에 `<NAME>_CLIENT_SECRET`. 서비스 쪽도 같은 환경 변수로 시크릿을 받는다
- [ ] `infra/compose/apps.yaml`에 서비스 추가: `<<: *spring-service`, 빌드 인자 `MODULE`, `depends_on`(config, 쓰면 postgres·kafka·redis), `<NAME>_DB_PASSWORD`, 서비스 포트의 `/actuator/health` healthcheck. 호스트 포트는 열지 않는다
- [ ] CI: `.github/workflows/ci.yml`의 백엔드 필터가 `services/**` 전체를 보므로 고칠 것이 없다. 서비스 밖 경로(예: 새 설정 디렉터리)를 테스트가 읽으면 필터에 더한다
- [ ] 라벨: `.github/labels.txt`에 `scope:<name>`이 없으면 더하고 `.github/apply-settings.sh` 실행
- [ ] overview 표와 roadmap 체크 항목 갱신

## 검증
- 빌드와 ArchUnit 테스트가 통과한다.
- Compose 인프라를 띄운 상태에서 서비스가 기동하고 Eureka에 등록된다.
- gateway를 거쳐 `/api/<name>/v1/...` 공개 API 하나가 응답한다. actuator는 gateway로 노출하지 않는다.
- outbox에 넣은 테스트 이벤트가 토픽에 나타난다.
