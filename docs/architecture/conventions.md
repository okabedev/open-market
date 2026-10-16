# 코드 규약

## 이름
| 대상 | 규칙 | 예 |
|---|---|---|
| 서비스 | 고유 명사 한 단어, 소문자. `-service` 같은 접미사를 붙이지 않는다 | `order`, `fulfillment` |
| Gradle 모듈 | `:core:<service>`, `:infra:<service>`, `:libs:<lib>` | `:core:order` |
| 루트 패키지 | `com.openmarket.<service>` | `com.openmarket.order` |
| DB 스키마·계정 | `<service>`, `<service>_app` | `order`, `order_app` |
| 토픽 | `<service>.<aggregate>` | `order.order` |
| Spring 앱 이름 | `<service>` (Eureka, config 파일 이름도 같다) | `spring.application.name=order` |

## 서비스 내부 패키지
core 서비스 전체와, 도메인 로직이 있는 infra 서비스(auth, file, notification)에 적용한다. gateway, discovery, config는 적용하지 않는다.

```
com.openmarket.<service>
├─ command
│  ├─ application   유스케이스, Command, 출력 포트(인터페이스)
│  └─ domain        집계, 엔티티, 값 객체, 도메인 이벤트, 저장소 인터페이스
├─ query
│  ├─ application   조회 서비스
│  ├─ dao           QueryDSL 조회 구현
│  └─ dto           View, 조회용 enum
├─ infra            JPA 저장소 구현, 외부 API 어댑터(Feign, 토스), Outbox 기록, 설정
└─ ui
   ├─ web           REST 컨트롤러, Request·Response
   └─ event         Kafka 리스너
```

### 의존 방향
`common-test`의 ArchUnit 규칙으로 빌드 때 강제한다.

| 패키지 | 의존할 수 있는 것 |
|---|---|
| `command.domain` | `libs/common-core`, JDK, Jakarta Persistence 애노테이션, JSpecify |
| `command.application` | `command.domain`, `common-core`, `common-messaging`(outbox 기록 인터페이스), Spring 트랜잭션 |
| `query.application` | `query.dao`, `query.dto` |
| `query.dao` | `query.dto`, QueryDSL, `command.domain`의 Q-타입(`Q*` 클래스)만 |
| `query.dto` | JDK, JSpecify, Jackson·OpenAPI 애노테이션 |
| `infra` | `command.domain`, `command.application`(포트 구현), 외부 라이브러리 |
| `ui` | `command.application`, `query.application`, `query.dto`, `common-web`, `common-messaging`(리스너의 멱등 소비, `EventEnvelope`) |

금지 규칙
- `query.*` ↛ `command.application`, 그리고 `command.domain`의 Q-타입이 아닌 클래스. 커맨드와 쿼리는 서로 모델을 공유하지 않는다.
- `ui` ↛ `command.domain`, `infra`.
- `infra` ↛ `ui`.
- `command.domain` ↛ Spring.

### 경계를 넘는 타입
- `Command`, 유스케이스 반환값, `Request`, `Response`, `View`에는 도메인 타입(엔티티, 값 객체, 도메인 enum)을 쓰지 않는다. 원시 타입, `String`, `java.time`, 중첩 record를 쓴다. 도메인 타입 변환은 유스케이스가 한다.
- 쓰기 입력에 enum이 필요하면 `Command` 안의 중첩 enum으로 두고 유스케이스가 도메인 enum으로 바꾼다.
- 조회 쪽 enum은 `query.dto`에 top-level enum으로 한 번만 둔다. 이름과 상수는 도메인 enum과 같게 한다. 두 enum의 상수 집합이 같은지는 서비스 테스트로 확인한다.
- dao가 enum 컬럼을 프로젝션할 때는 `path.stringValue()`로 문자열을 넘긴다. View는 문자열을 받아 조회용 enum으로 바꾼다.
- 출력 포트 시그니처는 이 제한을 받지 않는다. 포트는 도메인 타입을 써도 된다.

## 클래스 이름
| 종류 | 패키지 | 이름 |
|---|---|---|
| 유스케이스 | `command.application` | `<동사><명사>UseCase`, 메서드는 `execute(...)` 하나. 예: `PlaceOrderUseCase` |
| 커맨드 | `command.application` | `<동사><명사>Command` (record) |
| 출력 포트 | `command.application` | `<명사>Port`. 예: `PaymentGatewayPort` |
| 포트 구현 | `infra` | `<기술><명사>Adapter`. 예: `TossPaymentGatewayAdapter` |
| 저장소 | `command.domain` 인터페이스 / `infra` 구현 | `<집계>Repository` / `Jpa<집계>Repository` |
| 도메인 이벤트 | `command.domain` | 과거형. 예: `OrderPlaced` (context-map 이름과 같다) |
| 조회 서비스 | `query.application` | `<명사>QueryService` |
| 조회 DAO | `query.dao` | `<명사>QueryDao` |
| 조회 결과 | `query.dto` | `<명사>View`, `<명사>SummaryView` |
| 컨트롤러 | `ui.web` | 쓰기 `<명사>CommandController`, 읽기 `<명사>QueryController` |
| 요청·응답 | `ui.web` | `<동사><명사>Request`, `<명사>Response` |
| 리스너 | `ui.event` | `<이벤트>Listener`. 예: `OrderPlacedListener` |

위 표에 없는 보조 타입은 관련 클래스의 중첩 타입으로 둔다.

## REST API
- Gateway 경로 `/api/<service>/**`는 앞부분을 떼고 서비스로 넘긴다. 서비스는 `/v1/...`부터 받는다.
- 역할별 경로 접두어: 구매자·공개 `/v1/...`, 판매자 `/v1/seller/...`, 관리자 `/v1/admin/...`. Gateway와 서비스 둘 다 접두어로 역할을 검사한다.
- 리소스는 복수형 명사, kebab-case를 쓴다. 상태 전이는 하위 동작 경로로 둔다. 예: `POST /v1/seller/seller-orders/{id}/ship`.
- 목록은 커서 페이징(`?cursor=&size=`)이 기본이다. 관리자 화면처럼 쪽 번호가 필요한 곳만 오프셋을 쓴다.
- 상태 변경 API 중 중복되면 안 되는 것은 `Idempotency-Key`를 필수로 받는다([data.md](data.md)).
- 문서는 springdoc OpenAPI로 만든다. 프론트 `packages/api-client`가 이 문서에서 타입을 생성한다.

## 오류
- 베이스 예외는 `common-core`의 `BusinessException`이다. `ErrorType`(`INVALID`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `UNPROCESSABLE`, `UNAVAILABLE`)과 오류 코드 문자열을 가진다. HTTP 상태는 모른다.
- 도메인 예외는 이것을 상속한다. 오류 코드는 `<SERVICE>_<설명>` 형식이다. 예: `ORDER_PRICE_CHANGED`.
- 조회 쪽 "없음"은 `common-core`의 공통 `NotFoundException`을 던진다.
- `common-web`의 `@RestControllerAdvice` 하나가 `ErrorType`을 HTTP 상태로 바꾸고, RFC 9457 `ProblemDetail`로 응답한다. 확장 필드는 `code`, `traceId`, 필드 오류 목록(`errors`)이다. 서비스에는 예외 핸들러를 따로 두지 않는다.

## 테스트
| 종류 | 대상 | 도구 |
|---|---|---|
| 단위 | `command.domain`, 유스케이스(포트는 가짜 구현) | JUnit 5, AssertJ |
| 저장소·조회 | JPA 매핑, QueryDSL dao | Testcontainers PostgreSQL |
| 메시징 | outbox 기록, 리스너 멱등성, 이벤트 JSON 예시 호환 | Testcontainers Kafka 또는 리스너 직접 호출 |
| 웹 | 컨트롤러, 권한, 오류 응답 | MockMvc |
| 아키텍처 | 위 의존 방향과 이름 규칙 | ArchUnit (`common-test`) |
| E2E | 마일스톤 완료 시나리오 | Compose 전체 스택 + Playwright(화면)·HTTP 스크립트 |

- 기능은 테스트 먼저 작성한다(add-usecase skill).
- 테스트 이름은 한국어 `@DisplayName`으로 쓴다. 메서드 이름은 영어로 쓴다(Error Prone `UnicodeInCode`가 코드의 한글 식별자를 막는다).
- Testcontainers 컨테이너는 테스트 클래스 사이에서 재사용한다.

## 프론트엔드
프론트 규칙은 `clients/CLAUDE.md`에 둔다. 요약: App Router, 서버 컴포넌트 기본, 데이터 변경은 Server Action 또는 Route Handler(BFF)를 거친다. 브라우저 코드는 Gateway를 직접 부르지 않는다.

## 코드 스타일
빌드가 강제한다. 도구 선택 근거는 [ADR 0010](../adr/0010-code-style-tools.md)에 있다.
- 포맷: google-java-format AOSP 스타일(4칸). `./gradlew spotlessApply`로 고친다. 포맷이 틀리면 `check`가 실패한다.
- null: `com.openmarket` 아래는 null 아님이 기본이다. null이 될 수 있는 필드, 파라미터, 반환값에는 `org.jspecify.annotations.Nullable`을 붙인다. NullAway가 컴파일 때 검사한다.
- 컬렉션은 null 대신 빈 컬렉션을, 없을 수 있는 단건 조회는 `Optional`을 반환한다. `Optional`을 필드나 파라미터로 쓰지 않는다.
- Error Prone 경고를 `@SuppressWarnings`로 끌 때는 이유를 주석으로 남긴다.
