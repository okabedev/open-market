# 데이터

## 스키마와 계정
- PostgreSQL 인스턴스 하나에 데이터베이스 `openmarket` 하나를 둔다.
- 서비스마다 **같은 이름의 스키마와 DB 계정**을 만든다. 예: 스키마 `order`, 계정 `order_app`.
- 계정은 자기 스키마에만 권한이 있다. 다른 스키마는 `USAGE` 권한이 없어서 실수로 조인해도 실패한다.
- 만드는 곳은 `infra/postgres/init/`의 초기화 스크립트다. 서비스 목록은 `services.txt`에 있다. 스키마 안의 테이블은 각 서비스의 Flyway가 만든다.
- 스키마의 소유자는 서비스 계정이고, 계정의 `search_path`도 자기 스키마로 둔다. 서비스 설정의 `hikari.schema`(아래)와 같은 값이다.
- postgres-exporter(관측성)는 계정 `monitor`(`pg_monitor` 역할)를 쓴다. 통계 뷰와 복제 슬롯 상태만 읽고 업무 테이블은 읽지 못한다([observability.md](observability.md) "지표").
- Debezium은 복제 전용 계정(`debezium`)을 쓴다. 이 계정은 각 스키마의 `outbox_event` 테이블만 `SELECT`할 수 있다. 발행(publication) `openmarket_outbox`는 `outbox_event` 테이블들과 heartbeat 테이블만 포함하고, INSERT와 UPDATE만 복제한다.
  - `outbox_event`는 Flyway가 나중에 만들므로 초기화 때는 스키마 `USAGE` 권한만 준다.
  - 테이블 `SELECT` 권한과 publication 등록은 event trigger `publish_outbox_table`이 한다. 어느 스키마든 `outbox_event`라는 테이블이 생기면(`CREATE TABLE`) 바로 실행된다. 함수는 `SECURITY DEFINER`라 서비스 계정의 Flyway가 만들어도 관리자 권한으로 등록한다.
  - `debezium` 스키마의 `heartbeat` 테이블은 커넥터가 10초마다 고친다. outbox에 쓰기가 없어도 복제 슬롯이 앞으로 가게 한다([messaging.md](messaging.md#발행-transactional-outbox--debezium)).
- 복제 슬롯이 WAL을 붙잡아 디스크가 차지 않도록 `max_slot_wal_keep_size=1GB`를 둔다. 이 값을 넘으면 슬롯이 무효가 되어 커넥터를 다시 스냅샷해야 한다([risks.md](risks.md) R5).

### 연결 수
- 인스턴스 하나를 모든 서비스가 나눠 쓰므로 연결 수를 서비스 합계로 관리한다([ADR 0002](../adr/0002-single-postgres-schema-per-service.md)).
- Postgres는 기본값 `max_connections` 100(superuser 예약 3)이다. DB를 쓰는 서비스 12개(member, catalog, order, payment, fulfillment, promotion, review, settlement, search, auth, file, notification)가 Hikari 기본값(풀 10)이면 합계 120으로 넘친다. settlement를 더한 날(2026-10-25) `apps` profile에서 마지막에 뜬 search가 연결을 받지 못해 뜨지 않았다.
- 그래서 공통 `infra/config-repo/application.yml`에서 서비스마다 `spring.datasource.hikari.maximum-pool-size` 5로 둔다. 합계 60이고 Debezium 복제 연결, 운영 psql, 서비스를 하나 더 띄우는 경우의 여유가 남는다.
- `max_connections`를 올리지 않는 것은 연결마다 Postgres 메모리가 늘어 로컬 메모리 예산([risks.md](risks.md) R6)에 불리하기 때문이다. 부하 테스트(M6)에서 풀을 기다리는 서비스가 보이면 그 서비스의 `<service>.yml`에서만 늘리고 이 합계를 고친다.
- 테스트(Testcontainers)는 서비스마다 컨테이너가 따로라 이 예산과 상관없다.

서비스 설정에서 아래 값을 지정한다. 공통 라이브러리와 서비스의 SQL은 스키마 이름을 쓰지 않는다.
- `spring.datasource.hikari.schema`: 연결의 `search_path`. 공통 라이브러리의 JDBC 쿼리(outbox, 멱등 기록, ShedLock)와 서비스의 네이티브 쿼리는 스키마 이름 없이 테이블을 부르므로 꼭 필요하다
- `spring.jpa.properties.hibernate.default_schema`: Hibernate는 이 값을 테이블 이름 앞에 그대로 붙인다. `order`는 SQL 예약어라서 `order.yml`에서 따옴표로 감싼 `'"order"'`로 덮는다. 감싸지 않으면 모든 JPA 쿼리가 문법 오류다
- Flyway `schemas`와 `default-schema`: Flyway와 JDBC 드라이버(`hikari.schema`)는 이름을 스스로 따옴표로 감싸므로 그대로 둔다

예시는 `services/core/member`의 테스트 설정(`src/test/resources/config/application.yml`)이고, 실행 설정은 config-repo에 있다. 스키마·계정·search_path는 공통 `infra/config-repo/application.yml`이 서비스 이름(`${spring.application.name}`)으로 정하고, `<service>.yml`에는 비밀번호 자리 표시자만 둔다.

## 마이그레이션
- Flyway로만 스키마를 바꾼다. 위치는 `src/main/resources/db/migration`, 이름은 `V<번호>__<설명>.sql`이다.
- JPA `ddl-auto`는 `validate`로 둔다.
- 이미 적용된 마이그레이션 파일은 고치지 않는다. 새 파일을 추가한다.
- 컬럼 삭제나 이름 변경은 두 단계로 한다. 먼저 새 컬럼을 추가하고 코드를 옮긴 뒤, 다음 버전에서 옛 컬럼을 지운다.

## 공통 테이블
아래 테이블은 `common-messaging`과 `common-web`이 쓴다. DDL은 서비스에 복사하지 않고 라이브러리 jar 안의 Flyway 위치로 제공한다.

| 라이브러리 | Flyway 위치 | 테이블 |
|---|---|---|
| `common-web` | `classpath:db/common/web` | `idempotency_record`, `audit_log` |
| `common-messaging` | `classpath:db/common/messaging` | `outbox_event`, `processed_event`, `shedlock`, `dead_letter`(M6) |

- 서비스는 `spring.flyway.locations`에 자기 `classpath:db/migration`과 위 위치를 함께 적는다.
- 라이브러리 마이그레이션 버전은 `V0_<n>`이다. 서비스 마이그레이션은 `V1`부터 시작하므로 겹치지 않는다.
- 라이브러리들이 한 스키마의 Flyway 이력을 같이 쓰므로 `<n>`은 라이브러리 전체에서 이어 매긴다. 지금은 `V0_1` common-web, `V0_2`~`V0_4` common-messaging, `V0_5` common-web, `V0_6` common-messaging(`dead_letter`)이다. 다음 번호는 `V0_7`이다.
- 공통 테이블을 바꿀 때는 새 `V0_<n>` 파일을 추가한다. 이미 `V1` 이상이 적용된 서비스에도 들어가도록 config-repo 공통 설정에 `spring.flyway.out-of-order=true`를 둔다.

### outbox_event
| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | uuid PK | 이벤트 ID (UUID v7) |
| aggregate_type | varchar(100) | 토픽 이름. 예: `order.order` |
| aggregate_id | varchar(100) | Kafka 키 |
| type | varchar(100) | 이벤트 이름 |
| payload | jsonb | 이벤트 본문 |
| schema_version | int | 페이로드 버전 |
| traceparent | varchar(55) | W3C 트레이스 컨텍스트 |
| occurred_at | timestamptz | 발생 시각 |

### processed_event
| 컬럼 | 타입 | 설명 |
|---|---|---|
| event_id | uuid | 소비한 이벤트 ID |
| consumer | varchar(100) | 리스너 이름. 한 서비스가 같은 이벤트를 여러 리스너로 받을 수 있다 |
| processed_at | timestamptz | |

PK는 `(event_id, consumer)`다. 30일 지난 행은 지운다.

### shedlock
스케줄 작업이 여러 인스턴스에서 한 번만 실행되게 하는 락 테이블이다. 형식은 ShedLock `JdbcTemplateLockProvider` 문서를 따른다(`name` PK, `lock_until`, `locked_at`, `locked_by`). 시각은 DB 시계(UTC)로 비교한다.

### idempotency_record
| 컬럼 | 타입 | 설명 |
|---|---|---|
| idempotency_key | varchar(100) | 클라이언트가 보낸 키 |
| principal | varchar(100) | 요청자(토큰 subject). 다른 사용자 키와 섞이지 않게 한다 |
| request_hash | char(64) | 요청 본문의 SHA-256. 같은 키에 다른 본문이 오면 422로 거절한다 |
| status | varchar(20) | `IN_PROGRESS`, `COMPLETED` |
| response_status | int | |
| response_body | jsonb | 첫 응답 |
| created_at | timestamptz | 24시간 지나면 지운다 |

PK는 `(principal, idempotency_key)`다. 처리 중에 같은 키가 또 오면 409를 돌려준다.
- 5xx나 예외로 끝난 요청은 기록을 지워 같은 키로 다시 시도할 수 있게 한다.
- `IN_PROGRESS`가 1분 넘게 남아 있으면 앞선 요청이 중단된 것으로 보고 새 요청이 이어받는다.
- 24시간 지난 기록은 새 요청으로 처리한다. 지난 행은 `common-web`의 정리 작업이 한 시간마다 지운다([messaging.md](messaging.md#시간-기반-작업)).

### audit_log
관리자 작업 기록(NFR-8, 용어집 "감사 로그"). 유스케이스가 `common-core`의 `AuditLog`로 작업과 같은 트랜잭션에 쓴다. 지우거나 고치지 않는다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | uuid | UUID v7 |
| actor_id | varchar(100) | 작업한 사람(토큰 subject) |
| action | varchar(100) | 작업. 예: `SELLER_APPLICATION_APPROVED` |
| target_type, target_id | varchar | 대상. 예: `SELLER_APPLICATION`, 신청 ID |
| detail | varchar(1000) | 사유 같은 부가 설명. 개인정보를 넣지 않는다 |
| trace_id | varchar(32) | 요청의 trace id. 요청 로그와 잇는다 |
| occurred_at | timestamptz | |

인덱스는 `(target_type, target_id)`, `occurred_at`이다.

조회(ADM-4, M6): `common-web`이 서비스마다 `GET /v1/admin/audit-logs?from&to&actorId&action&before&size`를 연다(`AuditLogAdminController`). admin BFF가 서비스들의 결과를 합친다([ADR 0023](../adr/0023-admin-ops-queries.md)).
- `(occurred_at, id)` 내림차순이고 `{items, nextCursor}`를 준다. 항목은 ID, 작업한 사람, 작업, 대상 종류·ID, 부가 설명, trace id, 시각이다.
- 기간은 `from` 이상 `to` 미만(ISO-8601)이고 둘 다 선택이다. `size`는 1~100(기본 20)이다. 기간이 거꾸로이거나 크기·커서 형식이 틀리면 400 `AUDIT_LOG_INVALID_INPUT`이다.
- 커서는 `<시각(ISO-8601)>,<id>`를 base64url(패딩 없음)로 감싼 값이다. admin BFF는 합친 목록의 마지막 행으로 커서를 만들어 모든 서비스에 같이 보낸다. DLT 메시지 목록(`dead_letter`)도 같은 형식이다.
- 화면은 admin "감사 로그"(`/audit-logs`)다. 대상 서비스(`apps/admin/lib/admin-services.ts`, gateway 라우트가 있는 12개)에 동시에 묻고(서비스마다 5초, `lib/fan-out.ts`) 위 순서로 합친다(`lib/merged-page.ts`, DLT 메시지 화면도 같다). 응답하지 않은 서비스는 빼고 화면에 알린다. 거르기는 기간(KST 날짜, 종료일 포함), 서비스(그 서비스에만 묻는다), 작업, 작업한 사람이다. 형식이 틀린 커서는 첫 쪽으로 돌린다. 작업 이름은 `lib/audit-log.ts`의 `ACTION_LABELS`가 한국어로 바꾸고, 모르는 작업은 코드 그대로 보인다. 서비스가 새 작업을 남기면 여기에 더한다.
- 감사 대상은 관리자 작업이다(용어집 "감사 로그"). 판매자 작업(클레임 승인, 판매자 쿠폰, 상품 가격 등)은 남기지 않고 각 서비스의 이력과 이벤트로 본다. M6에서 금액을 바꾸는 관리자 작업을 점검해, 빠져 있던 정산 즉시 실행에 `SETTLEMENT_RUN_EXECUTED`를 더했다([settlement.md](settlement.md) "정산 확정").

### dead_letter
리스너 그룹이 처리하지 못해 DLT로 간 메시지(ADM-5, 용어집 "DLT 메시지"). `common-messaging`의 DLT 수집기가 넣는다. 규칙은 [messaging.md](messaging.md#dlt-재처리).

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | uuid | UUID v7 |
| consumer_group | varchar(100) | 실패한 리스너 그룹 |
| dlt_topic, dlt_partition, dlt_offset | varchar, integer, bigint | DLT 위치. 함께 유니크 |
| original_topic, original_partition, original_offset | varchar, integer, bigint | 원래 위치(`kafka_dlt-original-*` 헤더). 헤더가 없으면 DLT 토픽 이름에서 `.DLT`를 뺀 토픽, DLT 파티션, 오프셋 -1 |
| event_id, event_type | uuid, varchar(100) | 헤더 `id`, `type`. 읽을 수 없으면 비어 있다 |
| message_key | varchar | 원래 키 |
| payload | text | 원래 값. API로 내보내지 않는다 |
| headers | jsonb | 원래 헤더와 `kafka_dlt-*` 헤더. 순서대로 `[{key, value, base64}]`다. 같은 이름이 여럿일 수 있고 다시 보낼 때 그대로 되살린다. 값은 UTF-8 문자열이고, 읽을 수 없거나 탭·줄바꿈 밖의 제어 문자가 있으면(원래 파티션·오프셋 같은 바이너리 정수. jsonb는 `\u0000`을 받지 않는다) base64다 |
| exception_class | varchar(300) | `kafka_dlt-exception-cause-fqcn`, 없으면 `kafka_dlt-exception-fqcn` |
| trace_id | varchar(32) | `traceparent`의 trace id |
| status | varchar(20) | `PENDING`, `RETRIED`, `DISCARDED` |
| dead_lettered_at | timestamptz | DLT에 들어간 시각(레코드 타임스탬프) |
| resolved_at, resolved_by, discard_reason | timestamptz, varchar(100), varchar(500) | 다시 보내거나 버린 시각, 사람, 버린 사유 |

인덱스는 `(dead_lettered_at, id)`, `(status, dead_lettered_at)`, `event_id`다.

## 엔티티 규칙
- PK는 애플리케이션이 만드는 UUID v7이다. 외부에 노출하는 ID도 같은 값을 쓴다.
- 금액은 `bigint`(원 단위)다. 도메인에서는 `Money`로 감싼다.
- 시각은 `timestamptz`, 자바에서는 `Instant`다. 화면 표시만 `Asia/Seoul`로 바꾼다.
- 동시 수정이 있는 집계(`Stock`, `Coupon`, `Payment` 등)는 `@Version` 낙관적 락을 기본으로 한다. 경쟁이 심한 곳은 서비스 문서에 따로 적는다(예: payment는 비관적 락, 선착순 쿠폰은 Redis 앞단 필터 + 조건부 UPDATE).
- 조회 모델(`query`)은 같은 스키마의 테이블을 QueryDSL로 직접 읽는다. 다른 서비스의 데이터가 필요하면 이벤트로 만든 읽기 모델 테이블을 둔다.
- 개인정보 컬럼(이름, 전화번호, 주소, 계좌)은 DB에 평문으로 두되, 로그와 응답에서 마스킹한다. 본인에게 본인 정보를 주는 응답은 원문이다(NFR-7). 계좌 번호만 애플리케이션 단에서 암호화한다(`common-core`의 `AesGcmCipher`, 키는 환경 변수 `MEMBER_ACCOUNT_NUMBER_KEY`). 마스킹한 사본을 따로 둬서 조회할 때 복호화하지 않는다.

## 캐시와 기타 저장소
| 저장소 | 용도 |
|---|---|
| Redis | Gateway 레이트 리밋, BFF 세션, 선착순 쿠폰 발급 수·발급 회원 집합([promotion.md](promotion.md) "선착순 발급"), 상품 상세 캐시 |
| Elasticsearch | search의 `products` 인덱스(별칭 사용, 재색인은 새 인덱스 생성 후 별칭 전환) |
| 로컬 볼륨 | file의 원본과 썸네일. 컨테이너는 볼륨 `file-data`, IDE는 `infra/compose/data/file`([file.md](file.md)) |

Redis 키는 `<서비스>:<용도>:<id>`로 짓는다. 예: `promotion:coupon-stock:<couponId>`.

Redis를 쓰는 서비스(gateway, auth, promotion)는 Redis 클라이언트(Lettuce) 옵션을 같게 둔다(`RedisClientConfiguration`). 연결이 끊긴 동안 명령을 쌓지 않고 바로 실패시키고(`REJECT_COMMANDS`), 응답을 기다리는 명령은 연결마다 1,024개까지다. Lettuce 기본값은 끊긴 동안 명령을 무제한 쌓았다가 다시 연결되면 보내서, 장애 주입에서 gateway 힙이 차고 promotion이 다시 연결되는 순간 멈췄다([risks.md](risks.md) "장애 주입"). Redis에 닿지 못하면 레이트 리밋(gateway, auth 로그인)은 요청을 통과시키고 선착순 발급은 503 `PROMOTION_UNAVAILABLE`이다.
