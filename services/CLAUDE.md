# services (백엔드)

Gradle 멀티 모듈. 상세 규약은 [conventions.md](../docs/architecture/conventions.md), 데이터는 [data.md](../docs/architecture/data.md), 메시징은 [messaging.md](../docs/architecture/messaging.md)에 있다.

## 구조
```
services/
├─ settings.gradle.kts
├─ Dockerfile          서비스 공통 이미지(빌드 인자 MODULE=infra/discovery 등). compose가 쓴다. 힙은 컨테이너 상한의 50%
├─ build-logic/        convention 플러그인 (Java·Spring Boot·테스트·QueryDSL 공통 설정)
├─ gradle/libs.versions.toml
├─ libs/
│  ├─ common-core      베이스 예외, ErrorType, Money, ID(UUID v7), 개인정보 마스킹 (Spring 의존 없음)
│  ├─ common-web       ProblemDetail 변환, @Idempotent 처리, 요청 로깅 (자동설정)
│  ├─ common-messaging outbox 기록, 멱등 소비, EventEnvelope, 트레이스 전파, ShedLock·정리 작업 (자동설정)
│  └─ common-test      Testcontainers 설정, ArchUnit 규칙, 픽스처
├─ core/<service>
└─ infra/<service>     discovery(Eureka), config(Config 서버), gateway(WebFlux, ADR 0011) …
```

## 꼭 지킬 것
- 패키지는 `com.openmarket.<service>` 아래 `command/{application,domain}`, `query/{application,dao,dto}`, `infra`, `ui/{web,event}`로 나눈다. gateway, discovery, config는 예외다.
- 의존 방향과 이름 규칙은 `common-test`의 ArchUnit 규칙이 검사한다. 규칙을 우회하지 말고, 규칙이 틀렸다면 conventions.md와 규칙을 같이 고친다.
- `Command`, `Request`, `Response`, `View`, 유스케이스 반환값에는 도메인 타입을 쓰지 않는다.
- 상태를 바꾸는 서비스 사이 연동은 outbox 이벤트로만 한다. 유스케이스 안에서 Kafka로 직접 보내지 않는다.
- 리스너는 `common-messaging`의 `IdempotentEventProcessor`를 거친다. 비즈니스 실패는 예외가 아니라 실패 이벤트로 낸다.
- 스케줄 작업은 `common-messaging`의 `LockingTaskExecutor`(ShedLock)로 감싸 한 번만 실행되게 한다.
- Flyway 위치에 `classpath:db/migration`, `classpath:db/common/web`, `classpath:db/common/messaging`을 함께 적는다.
- 예외 핸들러는 서비스에 두지 않는다. `common-core` 예외를 던지면 `common-web`이 ProblemDetail로 바꾼다.
- 스키마는 Flyway(`db/migration/V<n>__<설명>.sql`)로만 바꾼다. `ddl-auto=validate`.
- 금액은 `long`(원)과 `Money`, 시각은 `Instant`, ID는 UUID v7이다.
- 설정은 `infra/config-repo/<service>.yml`에 둔다. 서비스 jar 안에는 config 서버 주소 같은 최소 설정만 둔다.

## 테스트
- 도메인과 유스케이스는 단위 테스트로 검증한다. 포트는 가짜 구현을 쓴다.
- 저장소, QueryDSL, 메시징은 Testcontainers로 검증한다.
- 발행하는 이벤트마다 JSON 예시(`src/test/resources/events/<type>.json`)를 둔다. 소비자 테스트는 발행자의 예시를 읽어 역직렬화를 확인한다.
- 테스트 이름은 한국어 `@DisplayName`, 메서드 이름은 영어로 쓴다.
- DB 통합 테스트는 `common-test`의 `PostgresContainerConfiguration`을, Redis가 필요하면 `RedisContainerConfiguration`을 `@Import`한다.
- 서비스 테스트 설정은 `src/test/resources/config/application.yml`에 둔다(main `application.yml`을 가리지 않게). 예: `core/member`.

## 명령
`services/`에서 실행한다.
| 명령 | 용도 |
|---|---|
| `./gradlew build` | 컴파일, 포맷 검사, 정적 분석, 테스트 |
| `./gradlew spotlessApply` | 포맷 자동 수정 |
| `./gradlew :core:<service>:test` | 서비스 하나만 테스트 |
| `./gradlew :core:<service>:bootRun` | 서비스 실행. 레포 루트 `.env`를 환경 변수로 읽고, config 서버(기본 profile)에서 설정을 받는다 |
| `./gradlew -p build-logic test` | convention 플러그인 회귀 테스트 (Java 25 도구 동작 확인) |

## 빌드 규약
- 모듈 빌드 파일은 convention 플러그인만 적용한다: 서비스 `openmarket.spring-service-conventions`, 라이브러리 `openmarket.library-conventions`, QueryDSL을 쓰면 `openmarket.querydsl-conventions`를 더한다.
- 버전은 `gradle/libs.versions.toml`에만 적는다. Boot·Cloud BOM이 관리하는 라이브러리는 버전 없이 쓴다.
- 포맷, Error Prone, NullAway 설정을 모듈에서 끄지 않는다.
