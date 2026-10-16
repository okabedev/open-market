# 데이터

## 스키마와 계정
- PostgreSQL 인스턴스 하나에 데이터베이스 `openmarket` 하나를 둔다.
- 서비스마다 **같은 이름의 스키마와 DB 계정**을 만든다. 예: 스키마 `order`, 계정 `order_app`.
- 계정은 자기 스키마에만 권한이 있다. 다른 스키마는 `USAGE` 권한이 없어서 실수로 조인해도 실패한다.
- 만드는 곳은 `infra/postgres/init/`의 초기화 스크립트다. 서비스 목록은 `services.txt`에 있다. 스키마 안의 테이블은 각 서비스의 Flyway가 만든다.
- 스키마의 소유자는 서비스 계정이고, 계정의 `search_path`도 자기 스키마로 둔다. 서비스 설정의 `hikari.schema`(아래)와 같은 값이다.
- Debezium은 복제 전용 계정(`debezium`)을 쓴다. 이 계정은 각 스키마의 `outbox_event` 테이블만 `SELECT`할 수 있다. 발행(publication) `openmarket_outbox`는 `outbox_event` 테이블들만 포함한다.
  - `outbox_event`는 Flyway가 나중에 만들므로 초기화 때는 빈 publication과 스키마 `USAGE` 권한만 만든다.
  - 테이블 `SELECT` 권한과 publication 등록은 커넥터 등록 스크립트(`infra/debezium`, M0-d)가 한다.
- 복제 슬롯이 WAL을 붙잡아 디스크가 차지 않도록 `max_slot_wal_keep_size=1GB`를 둔다. 이 값을 넘으면 슬롯이 무효가 되어 커넥터를 다시 스냅샷해야 한다([risks.md](risks.md) R5).

`order`는 SQL 예약어라서 스키마 이름을 따옴표로 감싸야 한다. 서비스 설정에서 아래 값을 지정해 쿼리에 스키마 이름이 직접 나오지 않게 한다.
- `spring.datasource.hikari.schema`: 연결의 `search_path`. 공통 라이브러리의 JDBC 쿼리(outbox, 멱등 기록, ShedLock)는 스키마 이름 없이 테이블을 부르므로 꼭 필요하다
- `spring.jpa.properties.hibernate.default_schema`
- Flyway `schemas`와 `default-schema`

예시는 `services/core/member`의 테스트 설정(`src/test/resources/config/application.yml`)이고, 실행 설정은 `infra/config-repo/<service>.yml`에 둔다(M0-c).

## 마이그레이션
- Flyway로만 스키마를 바꾼다. 위치는 `src/main/resources/db/migration`, 이름은 `V<번호>__<설명>.sql`이다.
- JPA `ddl-auto`는 `validate`로 둔다.
- 이미 적용된 마이그레이션 파일은 고치지 않는다. 새 파일을 추가한다.
- 컬럼 삭제나 이름 변경은 두 단계로 한다. 먼저 새 컬럼을 추가하고 코드를 옮긴 뒤, 다음 버전에서 옛 컬럼을 지운다.

## 공통 테이블
아래 테이블은 `common-messaging`과 `common-web`이 쓴다. DDL은 서비스에 복사하지 않고 라이브러리 jar 안의 Flyway 위치로 제공한다.

| 라이브러리 | Flyway 위치 | 테이블 |
|---|---|---|
| `common-web` | `classpath:db/common/web` | `idempotency_record` |
| `common-messaging` | `classpath:db/common/messaging` | `outbox_event`, `processed_event`, `shedlock` |

- 서비스는 `spring.flyway.locations`에 자기 `classpath:db/migration`과 위 위치를 함께 적는다.
- 라이브러리 마이그레이션 버전은 `V0_<n>`이다. 서비스 마이그레이션은 `V1`부터 시작하므로 겹치지 않는다.
- 라이브러리들이 한 스키마의 Flyway 이력을 같이 쓰므로 `<n>`은 라이브러리 전체에서 이어 매긴다. 지금은 `V0_1` common-web, `V0_2`~`V0_4` common-messaging이다. 다음 번호는 `V0_5`다.
- 공통 테이블을 바꿀 때는 새 `V0_<n>` 파일을 추가한다. 이미 `V1` 이상이 적용된 서비스에도 들어가도록 config-repo 공통 설정에 `spring.flyway.out-of-order=true`를 둔다(M0-c).

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

## 엔티티 규칙
- PK는 애플리케이션이 만드는 UUID v7이다. 외부에 노출하는 ID도 같은 값을 쓴다.
- 금액은 `bigint`(원 단위)다. 도메인에서는 `Money`로 감싼다.
- 시각은 `timestamptz`, 자바에서는 `Instant`다. 화면 표시만 `Asia/Seoul`로 바꾼다.
- 동시 수정이 있는 집계(`Stock`, `Coupon`, `Payment` 등)는 `@Version` 낙관적 락을 기본으로 한다. 경쟁이 심한 곳은 서비스 문서에 따로 적는다(예: payment는 비관적 락, 선착순 쿠폰은 Redis).
- 조회 모델(`query`)은 같은 스키마의 테이블을 QueryDSL로 직접 읽는다. 다른 서비스의 데이터가 필요하면 이벤트로 만든 읽기 모델 테이블을 둔다.
- 개인정보 컬럼(이름, 전화번호, 주소, 계좌)은 DB에 평문으로 두되, 로그와 응답에서 마스킹한다. 계좌 번호만 애플리케이션 단에서 암호화한다(키는 환경 변수).

## 캐시와 기타 저장소
| 저장소 | 용도 |
|---|---|
| Redis | Gateway 레이트 리밋, BFF 세션, 선착순 쿠폰 카운터, 상품 상세 캐시 |
| Elasticsearch | search의 `products` 인덱스(별칭 사용, 재색인은 새 인덱스 생성 후 별칭 전환) |
| 로컬 볼륨 | file의 원본과 썸네일 |

Redis 키는 `<서비스>:<용도>:<id>`로 짓는다. 예: `promotion:coupon-stock:<couponId>`.
