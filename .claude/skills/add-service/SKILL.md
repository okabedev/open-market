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
- [ ] `src/main/resources/application.yml`: 앱 이름, config 서버 import와 `fail-fast`, Flyway 위치만 둔다
- [ ] 테스트 설정은 `src/test/resources/config/application.yml`에 둔다. 같은 이름(`application.yml`)을 테스트 리소스 루트에 두면 main 설정을 가린다
- [ ] Flyway 위치에 공통 테이블 추가: `spring.flyway.locations=classpath:db/migration,classpath:db/common/web,classpath:db/common/messaging` ([data.md](../../../docs/architecture/data.md)). 서비스 마이그레이션은 `V1`부터
- [ ] ArchUnit 테스트: `common-test`의 `ArchitectureRules`를 이 서비스 패키지에 적용 (사용 예는 그 클래스의 Javadoc)
- [ ] 스키마와 계정: `infra/postgres/init/services.txt`에 이름 추가, `.env.example`과 `infra/compose/compose.yaml`의 postgres 환경 변수에 `<NAME>_DB_PASSWORD` 추가. 이미 뜬 DB에는 `docker compose down -v` 후 다시 띄워 적용한다
- [ ] `infra/debezium` 등록 스크립트: `debezium` 계정에 `<name>.outbox_event` SELECT 권한, publication `openmarket_outbox`에 테이블 추가
- [ ] `infra/debezium`: 커넥터 `table.include.list`에 `<name>.outbox_event` 추가
- [ ] `infra/kafka/topics`: 이 서비스가 발행하는 토픽과 `.DLT` 토픽 선언
- [ ] `infra/config-repo/<name>.yml`: 포트, DB 비밀번호 자리 표시자 `${<NAME>_DB_PASSWORD}`, Kafka 소비자 그룹 `<name>`. 스키마·계정·URL은 공통 `application.yml`이 서비스 이름으로 정한다
- [ ] gateway 라우트 `/api/<name>/**` → `lb://<name>` (외부 API가 있을 때)
- [ ] auth에 `client_credentials` 클라이언트 `<name>` 등록 (다른 서비스를 동기 호출할 때)
- [ ] `infra/compose`의 `apps` profile에 서비스 추가
- [ ] CI 변경 경로 필터에 모듈 추가
- [ ] overview 표와 roadmap 체크 항목 갱신

## 검증
- 빌드와 ArchUnit 테스트가 통과한다.
- Compose 인프라를 띄운 상태에서 서비스가 기동하고 Eureka에 등록된다.
- gateway를 거쳐 `/api/<name>/v1/...` 공개 API 하나가 응답한다. actuator는 gateway로 노출하지 않는다.
- outbox에 넣은 테스트 이벤트가 토픽에 나타난다.
