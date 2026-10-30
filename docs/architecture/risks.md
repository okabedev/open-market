# 기술 리스크

마일스톤 열의 시점에 확인한다. 확인하면 결과와 날짜를 적고, 결정이 생기면 ADR로 남긴다.

| # | 리스크 | 왜 문제인가 | 확인 방법 | 마일스톤 | 상태 |
|---|---|---|---|---|---|
| R1 | Java 25 + Spring Boot 4 + Spring Cloud 호환 조합 | Spring Cloud 릴리스 트레인이 쓰려는 Boot 버전을 지원해야 한다 | 공식 호환표와 start.spring.io 메타데이터로 확인하고, 빈 서비스를 빌드·기동한다 | M0-b | 확인(2026-10-16): Boot 4.1.1 + Cloud 2025.1.3, Java 25 지원. [ADR 0009](../adr/0009-version-matrix.md) |
| R2 | QueryDSL APT와 Java 25, Hibernate 7 | 원본 QueryDSL은 유지보수가 느리다. 포크(OpenFeign) 버전의 Jakarta·JDK 지원을 확인해야 한다 | 템플릿 서비스에서 Q-타입 생성과 조회 테스트 | M0-b | 확인(2026-10-16): QueryDSL 7.7 + `annotationProcessor` 경로로 Java 25에서 Q-타입 생성. `ConventionPluginsTest` |
| R3 | 포매터·정적 분석 도구의 Java 25 지원 | 도구가 새 문법을 못 읽으면 빌드가 깨진다 | 후보 도구로 템플릿 빌드 | M0-b | 확인(2026-10-16): palantir 제외, google-java-format 1.36.1 + Error Prone + NullAway 동작. [ADR 0010](../adr/0010-code-style-tools.md) |
| R4 | Gateway 구현 선택(WebFlux 또는 WebMVC) | 레이트 리밋, 토큰 처리 필터 지원 범위가 다르다 | 두 구현의 필터 문서 비교, 필요한 필터(JWT, Redis 레이트 리밋, 경로 재작성) 동작 확인 | M0-c | 확인(2026-10-16): WebFlux 선택. Redis 레이트 리밋을 설정으로 쓸 수 있다(WebMVC는 Bucket4j, 키 리졸버는 Java DSL만). JWT·역할·레이트 리밋·접두어 제거를 `GatewayTest`, `RateLimitTest`로 확인. Redis 무응답 시 기본 타임아웃(60s)만큼 요청이 멈추는 것을 발견해 200ms로 줄였다(`RedisOutageTest`). [ADR 0011](../adr/0011-gateway-webflux.md) |
| R5 | Debezium과 Postgres 논리 복제 | 스키마 여러 개의 outbox를 커넥터 하나로 읽고, 토픽을 `aggregate_type`으로 나눠야 한다. 복제 슬롯이 쌓이면 디스크가 찬다 | 샘플 서비스 두 개로 outbox → 토픽 확인, 커넥터 중지 후 재기동 시 유실 없는지 확인 | M0-d | 확인(2026-10-18): 스키마 두 개(member, catalog)의 outbox가 커넥터 하나로 각 토픽에 나가고, 커넥터·Kafka 중지 중에 쓴 행은 재기동 뒤 유실 없이 나왔다. heartbeat가 없으면 슬롯이 따라오지 못하고, 없는 토픽에서는 태스크가 RUNNING인 채 멈추며, Kafka 재접속 뒤 5분간 태스크를 다시 받지 못하는 것을 발견해 설정으로 고쳤다. 아래 "outbox 발행 확인" |
| R6 | 로컬 메모리 예산 | 서비스 15개, Kafka, Connect, ES, 관측성을 함께 띄워야 한다. WSL 메모리가 부족하면 E2E를 돌릴 수 없다 | profile별로 띄워 `docker stats`로 실측하고, 서비스 JVM 힙 기본값(`-XX:MaxRAMPercentage`)을 정한다 | M0-c | 확인(2026-10-17): 전체 profile 실측 5.1GB(Spring 서비스 4개). 상한이 없으면 JVM이 VM 메모리 기준으로 힙 상한을 5GB로 잡는 것을 발견했다. JVM 컨테이너에 `mem_limit` 512m + `MaxRAMPercentage=50`을 두었다. 서비스 15개 기준 컨테이너 상한 합계 약 11.5GB, 빌드까지 VM 전체 약 18GB로 `.wslconfig` 20GB를 권장한다. 아래 "메모리 예산". 재측정(2026-10-21, M3 마감): 서비스 11개 + 전체 profile에서 VM 14.4GB, order가 512m 상한에 7MB까지 닿아 Spring 서비스 상한을 640m·힙 40%로 바꿨다. 아래 "M3 재측정". 재측정(2026-10-22, M4 마감): 서비스 12개, 상한 도달·OOM 없음. notification이 가장 크다(peak 564MB/640MB). 아래 "M4 재측정". 중간 측정(2026-10-25, M5 settlement 골격): 서비스 15개가 모두 뜬 첫 측정. 대기 상태에서 상한 도달·OOM 없음, VM 14.5GB. 연결 수 합계가 Postgres 한도를 넘는 것을 찾아 서비스당 풀을 5로 정했다. 아래 "M5 중간 측정". 재측정(2026-10-26, M5 마감): 서비스 15개로 M5 시나리오 3개를 돈 직후 상한 도달·OOM 없음, VM 12.8GB. notification peak 534MB/640MB로 M4보다 여유가 생겼다. 상한은 그대로다. 아래 "M5 재측정". 부하 중 측정(2026-10-27, M6 k6): 서비스 15개와 모든 profile에서 NFR-9 시나리오를 돌렸다. 기본 부하에서는 상한 도달·OOM 없음. 조회·검색 초당 600건에서 catalog가 640m 상한에 닿았다(상한 도달 83회, anon 627MB, 사용량이 떨어지지 않아 재시작은 없었다). 컨테이너 메모리 합계 최대 약 13.0GB. 성능 개선 뒤(gateway·catalog ParallelGC) 초당 600건에서 catalog peak 637MB/640MB, 상한 도달·재시작 없음. 아래 "부하 측정". 쿠폰 버스트 재측정(2026-10-29): promotion이 640m 상한에 실행마다 85~187회 닿아(anon 628MB, 힙 247/256MB) 768m로 올렸다. 아래 "메모리 예산"의 예외 |
| R7 | auth issuer 호스트 이름 | 브라우저, IDE 실행 서비스, 컨테이너가 같은 issuer URL로 auth에 닿아야 한다 | hosts + 네트워크 별칭으로 세 경우 모두 토큰 검증 확인 | M1 | 확인(2026-10-18): issuer를 고정하면 요청 주소와 상관없이 `iss`가 같다(`AuthorizationServerIntegrationTest`, 무작위 포트·다른 Host). 호스트(curl, hosts 대신 `--resolve`)에서 PKCE 로그인으로 받은 토큰을 컨테이너 gateway가 검증했다. 네트워크 별칭은 auth를 IDE로 띄우면 사라져 JWK를 받지 못해, 컨테이너가 `host-gateway`로 호스트 포트에 닿게 바꿨다. auth가 컨테이너일 때와 IDE일 때 모두 검증 통과. IDE gateway(bootRun)도 hosts 항목을 넣은 뒤 같은 결과였다(유효 토큰 통과, 서명 위조·토큰 없음 401). IDE gateway는 Netty DNS 리졸버가 `/etc/hosts`만 읽어서, JVM 옵션 `jdk.net.hosts.file`로는 대신할 수 없다. [ADR 0013](../adr/0013-auth-issuer-host.md) |
| R8 | Next.js BFF 세션과 토큰 갱신 | 서버 컴포넌트, Server Action, Route Handler 모두에서 세션을 읽고 토큰을 갱신해야 한다. 동시 갱신 경쟁이 생길 수 있다 | 갱신 중복 방지(Redis 락) 시나리오 테스트 | M1 | 확인(2026-10-18): Redis 락(`SET NX PX`)을 잡은 한 요청만 갱신하고 나머지는 기다린다. BFF 인스턴스 둘이 동시에 20번 갱신해도 auth 요청은 1번이다(`bff.test.ts`, 락을 끄면 2번으로 실패). 실제 auth와 브라우저에서 액세스 토큰을 강제 만료한 뒤 서버 컴포넌트 5·Route Handler 5·Server Action 1을 동시에 보내기를 3번 반복했다. 모두 인증됐고 세션이 유지됐다. [ADR 0014](../adr/0014-bff-session.md) |
| R9 | 토스페이먼츠 테스트 환경 | 테스트 키로 승인, 부분취소, 웹훅을 로컬에서 받을 수 있어야 한다. 웹훅은 공인 주소가 필요하다 | 승인·취소 API 확인. 웹훅은 결제 조회 폴링으로 대신할 수 있는지 확인 | M3 | 확인(2026-10-20): 주문서형 연동 키는 전자결제 신청(사업자 정보) 뒤에만 나와서 문서용 공개 테스트 키를 쓴다. 위젯 결제 → 서버 승인 → 조회 → 부분취소 → 나머지 취소가 로컬에서 됐고, 토스 `Idempotency-Key`로 승인·취소를 다시 보내도 한 번만 처리됐다. 웹훅 없이 `paymentKey` 조회로 상태를 확정할 수 있다. `orderId` 조회는 문서용 키에서 404라 쓰지 않는다. 아래 "토스 테스트 환경 확인". [ADR 0018](../adr/0018-toss-payments-integration.md) |
| R10 | Elasticsearch nori 플러그인 | 공식 이미지에 nori가 없어서 커스텀 이미지가 필요하다 | Dockerfile로 플러그인 설치, 인덱스 분석기 테스트 | M2 | 확인(2026-10-19): 공식 9.4.6 이미지에 `analysis-nori` 9.4.6을 설치한 이미지가 뜨고, 메모리는 1.5GB로 같다. 색인·검색 `decompound_mode` 조합 셋을 상품명으로 비교했다. 검색도 `mixed`로 해야 붙여 쓴 원형(`사과즙`)이 띄어 쓴 문서보다 위로 오고, `operator: and`에서도 띄어 쓴 문서를 놓치지 않았다. [ADR 0016](../adr/0016-elasticsearch-nori.md) |
| R11 | 선착순 쿠폰 발급의 정합성 | Redis 카운터·회원 집합과 DB 발급 행이 어긋날 수 있다(Redis 차지 뒤 DB 저장 실패, Redis 재시작으로 키 유실, 키를 다시 만드는 사이 끼어든 요청). 어긋나면 수량을 넘겨 발급하거나 1인 2매가 된다(PRM-2) | 동시 요청(수량보다 많은 회원, 같은 회원의 중복 요청)을 몰아 넣고 DB 발급 수 ≤ 수량, 회원당 1장을 확인한다. DB 저장 실패와 Redis 키 삭제를 끼워 같은 확인을 한다 | M5 | 확인(2026-10-23). DB 조건부 발급 수와 유니크를 마지막 방어선으로 두고 Redis는 앞단 필터로 쓴다. 결과와 남는 어긋남은 [promotion.md](promotion.md) "정합성". [ADR 0020](../adr/0020-first-come-coupon-issuance.md) |
| R12 | 주문 예약 두 개(재고·쿠폰)의 합류 | order가 다른 토픽의 두 결과를 모아야 한다. 결과가 어떤 순서로 와도, 한쪽이 실패해도, 같은 결과를 다시 받아도 주문 상태가 하나로 정해지고 받은 예약이 풀려야 한다 | 두 결과의 순서(재고 먼저, 쿠폰 먼저, 동시)와 실패 조합마다 주문 상태와 catalog·promotion 예약을 확인한다. 동시 도착은 두 리스너를 같은 주문에 동시에 돌린다 | M5 | 확인(2026-10-23). 성공 결과를 주문에 따로 기록하고 주문 행 잠금으로 하나씩 처리한다. 순서·중복·실패 조합(Kafka)과 동시 처리(두 스레드 × 20회)에서 상태가 하나로 정해지고 주문 이벤트가 한 번만 나갔다(`ReservationJoinIntegrationTest`). 예약 해제는 catalog·promotion의 `OrderCancelled` 처리 테스트가 맡고, 서비스를 모두 띄운 확인은 M5 완료 기준 E2E에서 한다. M5 E2E(2026-10-26)에서 재고·쿠폰 예약이 함께 성공한 주문이 결제되고 쿠폰이 사용됨이 됐다(`m5-buyer-coupon`, `m5-completion`). 실패 조합은 E2E에서 보지 않았다. 규칙은 [order.md](order.md) "예약 결과". [ADR 0021](../adr/0021-order-reservation-join.md) |
| R13 | 로컬 부하 측정의 신뢰도 | k6와 서비스 15개, Kafka, ES가 한 VM(CPU 6개)에서 돈다. 부하 발생기가 CPU를 나눠 쓰고, gateway 레이트 리밋(NFR-5)이 요청을 429로 끊어 서비스 지연을 재지 못할 수 있다 | k6 컨테이너와 서비스의 CPU 사용률을 함께 기록한다. 레이트 리밋을 그대로 둔 측정과 부하용 한도로 올린 측정을 나눠, NFR-9는 후자로, 레이트 리밋 동작은 전자로 본다. 같은 시나리오를 세 번 돌려 p95 편차를 본다 | M6 | 확인(2026-10-27): 한도를 올리지 않고 k6가 BFF 자리에서 사용자마다 다른 `X-Forwarded-For`를 넣는다(gateway는 그 마지막 값을 키로 쓴다). 레이트 리밋 설정을 바꾸지 않은 채 서비스 지연을 쟀다. 기본 부하(조회·검색 초당 100건)에서 k6 CPU는 평균 8%, 전체 CPU 평균 157~229%/600%였다. 예열 뒤 같은 시나리오 세 번의 p95는 상세 9.9~14.4ms, 검색 10.9~19.0ms였다. 초당 600건에서는 전체 CPU가 평균 460%로 차고 k6도 33%를 써서, 이 구간의 숫자는 서비스 한계와 부하 발생기 경합이 섞인다. 기동 직후 첫 요청들은 JIT 예열로 p95가 230~255ms라 30초 예열 단계를 두고 측정 단계에만 임계값을 건다. 레이트 리밋 동작(한 IP에서 429)은 보안 점검에서 본다. 아래 "부하 측정" |
| R14 | DLT 재처리의 전달 범위 | DLT는 토픽마다 하나라 원래 토픽으로 다시 보내면 모든 그룹이 받는다. 처리 기록(`processed_event`)은 30일 뒤 지워져 다른 그룹이 다시 처리할 수 있다 | 두 그룹이 같은 토픽을 읽고 한 그룹만 실패한 메시지를 다시 보낸다. 처리 기록을 지운 상태에서도 실패한 그룹만 처리하는지 통합 테스트로 본다. 다시 보낸 메시지가 또 실패하면 새 행으로 들어오는지 본다 | M6 | 확인(2026-10-27): 두 그룹(catalog, promotion)이 같은 토픽을 읽고 catalog만 실패해 DLT로 간 이벤트를, promotion의 처리 기록을 지운 뒤 다시 보냈다. catalog만 한 번 처리했고 promotion은 `dlt-target-group`을 보고 넘겼다(`DeadLetterRetryIntegrationTest`). 다시 보낸 메시지는 원래 토픽·파티션에 원래 키·값·헤더로 가고 `kafka_dlt-*` 헤더는 빠진다(`DeadLetterAdminIntegrationTest`). 또 실패하면 수집기가 새 DLT 위치로 새 행을 넣는다(같은 이벤트 ID). [ADR 0022](../adr/0022-dead-letter-reprocessing.md) |
| R15 | 관리자 TOTP와 인가 서버 로그인 흐름 | 비밀번호와 TOTP 사이에 인가 코드가 나가면 안 된다. 폼 로그인 성공 처리, 잠금 횟수, 로그인 레이트 리밋, 인가 요청 저장(돌아갈 주소)이 두 단계에 걸쳐 맞아야 한다 | 비밀번호만 맞힌 세션으로 `/oauth2/authorize`를 불러 코드가 안 나오는지, 같은 코드 재사용, 틀린 코드 5회 잠금, 등록 전 관리자의 첫 로그인을 통합 테스트로 본다. Spring Security 7 다중 요소 인증 지원과 직접 구현을 비교한다 | M6 | 확인(2026-10-28): Spring Security 7.1.1은 인가 코드를 내는 필터가 권한 검사 뒤에 있어, 인가 엔드포인트 체인이 ADMIN 로그인에 `FACTOR_TOTP` 요소를 요구하면 비밀번호만 맞힌 세션은 코드를 받지 못한다([ADR 0024](../adr/0024-admin-totp-mfa.md)). `AdminSecondFactorIntegrationTest`: 비밀번호만 맞힌 세션의 인가 요청(admin, shop)은 2단계 화면으로 가고 코드가 없다. 등록 전 첫 로그인은 등록 뒤 저장한 인가 요청으로 돌아가 코드를 받는다. 같은 코드 재사용 거절, 틀린 코드 5회째 잠금(다시 로그인해도 횟수가 이어진다), 복구 코드 한 번, 2단계 요청도 로그인 레이트 리밋. 관리자가 아닌 계정은 그대로다. 관리자가 비밀번호 성공으로 실패 횟수를 지우면 코드를 끝없이 추측할 수 있는 점을 찾아 2단계가 맞을 때만 지우게 했다 |
| R16 | 장애 중 Saga 정합성 | Kafka 중지, DB 지연, PG 지연 중에 재시도·타임아웃·보상이 겹치면 이벤트 유실, 중복 처리, 끝나지 않는 주문이 생길 수 있다. 복제 슬롯 무효(R5에서 남긴 것), 쿠폰 예약 실패 조합과 Redis 장애 중 발급(ADR 0020·0021에서 남긴 것)도 서비스를 모두 띄운 상태로 보지 않았다 | 서비스를 모두 띄운 스택에서 주문을 넣으며 장애를 넣고 풀어, 모든 주문이 끝 상태(결제·취소)로 가는지, outbox와 토픽·`processed_event` 수가 맞는지, DLT가 비었는지 센다. DB 지연은 Toxiproxy, PG 지연은 가짜 PG로 넣는다 | M6 | 확인(2026-10-29): 마감에서 Kafka 1분 중지를 다시 돌려 주문 361건 모두 결제 완료, 위반 0, DLT 0. 장애 종류(Kafka, DB 지연·끊김, PG 지연, 복제 슬롯 무효, Redis, 쿠폰 예약 실패 조합)를 모두 봤고, 슬롯 무효의 경보와 복구 절차는 [런북](../runbook.md)에 있다. 처음 확인(2026-10-27): `infra/fault/run.sh`로 서비스를 모두 띄운 스택에서 주문·결제를 흘리며 Kafka 1분 중지, DB 지연 300ms ± 100ms 1분, DB 연결 끊김 1분, PG 승인 응답 15초 지연을 넣었다. 네 경우 모두 끝나지 않은 주문·결제 0, 주문·결제·재고 예약의 짝과 SKU별 재고 차감이 맞았고 DLT 메시지 0이었다(주문 1,441건 + 720건). DB가 느릴 때 gateway 타임아웃 뒤에도 주문이 만들어져 같은 키의 재요청이 409 `COMMON_IDEMPOTENCY_IN_PROGRESS`를 받는 것을 찾았다(설계대로, 결함 아님). 추가(2026-10-27): 쿠폰 예약 실패 조합 30경우는 모두 한 주문만 결제됐고 위반 0. Redis 장애 중 발급은 초과 발급 없이 503으로 막혔다. 복제 슬롯 무효는 운영자가 슬롯을 지워야 복구되고(R5), Redis 장애 중 요청이 몰리면 gateway가 힙을 다 써서(OOM) 응답하지 못하는 결함과 Redis가 돌아오는 순간 promotion이 10초 넘게 멈추는 결함을 찾아 고쳤다(Lettuce가 끊긴 동안 명령을 쌓는 기본값). 아래 "장애 주입" |

## outbox 발행 확인 (R5)
환경(2026-10-18): 기본 profile을 빈 볼륨으로 띄우고, member·catalog 계정으로 `outbox_event`를 만든 뒤(Flyway와 같은 DDL) 행을 직접 넣었다. 토픽은 `kafka-console-consumer`로 읽고 `id` 헤더로 중복을 셌다. 설정 설명은 [messaging.md](messaging.md#발행-transactional-outbox--debezium)에 있다.

| 시나리오 | 결과 |
|---|---|
| 서비스 계정으로 `outbox_event` 생성 | event trigger가 publication에 넣고 debezium에 `SELECT`만 줬다 |
| 스키마 두 개에 쓰기 | `member.member`, `catalog.product`로 나뉘어 나갔다. 키는 `aggregate_id`, 값은 페이로드 JSON 원문, 헤더는 `id`·`type`·`occurredAt`(UTC `Z`)·`schemaVersion`·`traceparent`. 비어 있는 `traceparent`는 문자열 `"null"`이 아니라 null 값이다 |
| Connect를 멈춘 동안 500행 × 2 | 재기동 뒤 각 500건, 중복 없음 |
| Kafka를 60초 멈춘 동안 400행 × 2 | 기본 설정에서는 Kafka가 돌아온 뒤 5분 동안 발행이 멈췄다(태스크 상태는 RUNNING). 워커가 그룹에 다시 들어와도 리밸런스 지연(`scheduled.rebalance.max.delay.ms`) 동안 태스크를 받지 못했다. 0으로 바꾼 뒤 15초 안에 모두 발행, 유실 없음 |
| outbox가 아닌 테이블에 WAL 52MB | heartbeat 쿼리가 있으면 90초 안에 슬롯이 따라왔다(남은 차이 656B). 없으면 90초 뒤에도 52MB 뒤처져 있었다 |
| 선언하지 않은 토픽(`member.unknown`)에 쓰기 | 기본 설정에서는 태스크가 RUNNING인 채 프로듀서가 기다려, 뒤따른 다른 토픽 이벤트까지 멈췄다. `max.block.ms` 30초로 바꾸자 37초 뒤 FAILED, 66초 뒤 connect unhealthy. 토픽을 만들고 태스크를 재시작하자 두 이벤트 모두 한 번씩 나갔다 |
| 스냅샷(임시 커넥터, `initial_only`) | 남아 있는 outbox 행을 Router가 처리해 같은 `id`로 다시 보냈다. 소비자 멱등으로 거른다 |
| `docker compose up -d --wait` 반복 | 두 번째부터 토픽 0개 생성, 커넥터 설정 갱신(HTTP 200), 종료 코드 0 |

슬롯 무효(2026-10-27, M6 장애 주입에서 확인): 슬롯이 `max_slot_wal_keep_size`를 넘어 무효(`lost`)가 되면 Debezium은 스스로 복구하지 못한다. 커넥터·태스크는 `RUNNING`인 채 2초마다 슬롯을 다시 얻으려고 하고(900번, 약 30분) 발행은 멈춘다. Connect 헬스체크는 `FAILED`만 보므로 알아채지 못한다. 슬롯을 지우면(`pg_drop_replication_slot`) Debezium이 새 슬롯을 만들고 "마지막 오프셋을 쓸 수 없다"며 `when_needed` 스냅샷으로 남아 있는 outbox 행을 다시 보낸다. 소비자가 멱등이라 중복은 걸러지고 유실은 없었다. outbox 행은 7일 보관이므로 그보다 오래 멈추면 유실된다. 아래 "장애 주입"

## 토스 테스트 환경 확인 (R9)
환경(2026-10-20): 문서용 공개 테스트 키(`.env.example`), SDK v2 `https://js.tosspayments.com/v2/standard`, 확인용 페이지를 로컬(`localhost`)에 띄우고 Windows 브라우저에서 결제했다. 승인 이후 단계는 `successUrl`을 받은 서버가 이어서 불렀다. 결제는 끝에 전액 취소했다.

| 확인 | 결과 |
|---|---|
| 위젯 렌더링 | 테스트 환경 안내와 결제수단(퀵계좌이체, 카드, 토스페이, 페이코, 카카오페이, 네이버페이)이 나온다. 가상계좌는 없다 |
| 결제수단별 인증 | 카드는 카드사의 실제 인증 화면(예: `customer.kbcard.com`), 토스페이는 토스 앱 QR이다. 자동화할 수 없어 사람이 한다. 카드사를 고르지 않고 결제하면 SDK가 `NEED_CARD_PAYMENT_DETAIL`로 거절한다 |
| `successUrl` | `paymentType=NORMAL`, `orderId`, `paymentKey`(`tgen_` 접두어), `amount=15000` |
| 승인 전 `GET /v1/payments/orders/{orderId}` | 404 `NOT_FOUND_MERCHANT`. 승인 뒤에도 같다. 문서용 키(상점 `tgen_docs`)에서는 `orderId` 조회를 쓸 수 없다 |
| 금액을 바꾼 승인 | 403 `FORBIDDEN_REQUEST`. 그 뒤 맞는 금액으로 승인하면 성공한다 |
| 승인 | 200, 854ms. `status=DONE`, `totalAmount=15000`, `balanceAmount=15000`, `method=간편결제`. 토스페이 즉시 할인(16원)은 `easyPay.discountAmount`에만 나오고 `totalAmount`는 요청 금액 그대로다 |
| 같은 `Idempotency-Key`로 승인 재요청 | 200, 50ms, 첫 응답과 같다 |
| 키 없이 승인 재요청 | 400 `ALREADY_PROCESSED_PAYMENT`. 이미 승인된 결제다. 조회로 확인해야 한다 |
| 부분취소 5,000원 | 200, 1,135ms. `PARTIAL_CANCELED`, 잔액 10,000. `cancels[]`에 `transactionKey`, `cancelAmount`, `canceledAt` |
| 같은 키로 부분취소 재요청 | 200, 62ms, 취소는 하나만 남는다 |
| 나머지 전액 취소(금액 없이) | 200. 잔액 0인데 상태는 `CANCELED`가 아니라 `PARTIAL_CANCELED`로 남는다 |
| 취소된 결제 다시 취소 | 403 `NOT_CANCELABLE_AMOUNT` |
| `GET /v1/payments/{paymentKey}` | 200. 승인·취소 내역이 모두 나온다 |
| `TossPayments-Test-Code: REJECT_CARD_PAYMENT` | 403 `REJECT_CARD_PAYMENT`. 없는 결제에도 지정한 오류가 그대로 온다 |
| 잘못된 시크릿 키 | 401 `UNAUTHORIZED_KEY` |
| 없는 `paymentKey`로 승인 | 404 `NOT_FOUND_PAYMENT_SESSION`(결제 진행 데이터 만료와 같은 코드) |
| 퀵계좌이체 테스트 흐름(2026-10-21, shop 결제 화면) | 휴대폰 번호가 미리 들어 있고 "다음" → 테스트 비밀번호 `000000`(키패드) → 결제하기 → 비밀번호 `000000`이면 `successUrl`로 돌아온다. 헤드리스 Chromium에서 Playwright로 끝까지 됐다. 승인 응답의 `method`는 `계좌이체`다. 이 결제를 보상 취소 작업으로 전액 취소하자 `CANCELED`, 남은 금액 0이었다 |

설계에 반영한 것([ADR 0018](../adr/0018-toss-payments-integration.md))
- 승인 타임아웃·재조회는 `orderId`가 아니라 `paymentKey`로 조회한다. 승인 요청에 `paymentKey`가 있으므로 `IN_PROGRESS` 결제는 늘 `paymentKey`를 갖는다.
- 결제 상태는 토스 `status`만 보지 않는다. 취소 결과는 `balanceAmount`가 0이면 전액 취소로 본다.
- 금액 대조는 `totalAmount`로 한다. 결제수단의 즉시 할인이 있어도 `totalAmount`는 요청 금액 그대로다.
- 키 없이 다시 보낸 승인의 `ALREADY_PROCESSED_PAYMENT`는 실패가 아니다. 조회로 실제 상태를 확인한다.

## 메모리 예산 (R6)
측정 환경(2026-10-17): Docker Desktop 29.8.2(WSL2), `.wslconfig` `memory=20GB`(VM에서 보이는 값 19.5GB), CPU 6개. `docker compose --profile search --profile obs --profile apps up` 후 gateway 경유로 20,000건(동시 16)을 보내고 `docker stats`로 쟀다. JVM 값은 `/actuator/prometheus`의 `jvm_memory_*`다.

| 구성 | 개수 | 가정 | 실측 | 상한 |
|---|---|---|---|---|
| Spring 서비스(discovery, config, gateway, member) | 4 | 300~400MB | 230~335MB. 힙 사용 40~65MB, 힙 밖(메타스페이스, 코드 캐시) 80~126MB | 512MB, 힙 248MB |
| PostgreSQL, Redis, Mailpit | 3 | 합계 1GB | 합계 95MB | 없음 |
| Kafka(KRaft), Debezium Connect, Kafka UI | 3 | 합계 2.5GB | 433MB, 1.0GB, 291MB. 합계 1.7GB | Kafka·Connect 힙 512MB, Kafka UI 512MB |
| Elasticsearch | 1 | 1.5GB (힙 1GB) | 1.5GB | 힙 1GB |
| 관측성(Collector, Prometheus, Tempo, Loki, Grafana) | 5 | 합계 1.5GB | 합계 640MB(Grafana 373MB) | 없음 |
| **합계** | 16 | | **5.1GB** | |
| Docker 엔진·WSL 자체(VM `free` 사용량 7.1GB − 컨테이너 합계) | | | 약 2GB | |
| `./gradlew build` 중 증가분(테스트 워커, Testcontainers) | | | 캐시 사용 1.7GB, `--rerun-tasks` 4.6GB | |

결정
- Spring 서비스와 Kafka UI 컨테이너에 `mem_limit: 512m`을 두고 힙을 그 50%(248MB)로 한다. Spring 서비스는 `services/Dockerfile`의 `JDK_JAVA_OPTIONS=-XX:MaxRAMPercentage=50`, Kafka UI는 `JAVA_OPTS`로 준다.
  - 상한이 없으면 JVM은 VM 메모리(19.5GB)의 25%인 약 5GB를 힙 상한으로 잡는다. 서비스가 15개면 한두 개가 힙을 키울 때 다른 컨테이너가 밀려난다.
  - 최악의 경우 힙 248MB + 힙 밖 약 130MB + 스레드 스택·네이티브 약 60MB로 512MB 안에 든다. 힙이 차면 컨테이너가 OOM으로 죽지 않고 JVM이 `OutOfMemoryError`를 낸다.
  - 25%·50%·60%와 512m·768m을 member로 비교했다. 부하 중 사용량은 325~345MB로 비슷했고, OOM이나 재시작은 없었다.
  - 2GB 미만 컨테이너라 JVM이 SerialGC를 고른다. 20,000건에 minor GC 41회, 합계 0.2s로 이 크기 힙에는 충분하다. M6 부하 측정에서 gateway·catalog는 SerialGC 멈춤이 지연의 큰 몫이라 이 둘만 ParallelGC로 덮어썼다(아래 "부하 측정"의 "성능 개선"). 바꿀 필요가 생기면 서비스별로 `JDK_JAVA_OPTIONS`를 덮어쓴다.
- Kafka, Connect, Elasticsearch는 이미지 설정으로 힙을 고정했으므로 `mem_limit`을 두지 않는다. 나머지는 사용량이 작아 두지 않는다.
- M2(2026-10-20): `apps`에 catalog, file, search가 더해지고 Elasticsearch가 `apps` profile에서도 뜬다(search가 쓴다). search 컨테이너는 검색·색인 확인 중 327MB/512MB였다. 서비스 전체 재측정은 M3(주문·결제 서비스가 더해진 뒤)에 한다.
- M3(2026-10-20): `apps`에 order가 더해졌다. 장바구니 확인 직후 410MB/512MB로 member(406MB)·catalog(404MB)와 같은 수준이다(OpenFeign, Resilience4j, OAuth2 클라이언트 포함).
- M4(2026-10-21): `apps`에 fulfillment가 더해졌다. 기동하고 `OrderPaid`를 처리한 직후 약 390~400MB/640MB로 order(약 410~435MB)보다 조금 작다. 서비스 15개 재측정은 M5 마감 전에 한다.
- 예외: promotion은 `mem_limit: 768m`, 힙 40%(약 307MB)다(2026-10-29).
  - 측정: k6 coupon(구매자 1,000명 동시 발급)을 같은 스택에서 640m으로 두 번, 768m으로 두 번 돌렸다. 컨테이너 cgroup의 `anon`, `memory.peak`, 상한 도달 수와 Prometheus JVM 지표를 봤다.
  - 640m: anon 593·628MB, peak 638·640MB, 상한 도달 0·187회(두 번째 실행). 버스트 직후 커밋된 힙 247.5MB/256MB, 비힙 207MB(메타스페이스 155MB), 스레드 53개. 힙과 컨테이너가 함께 빠듯해 M6 마감 때 major GC 283~410ms가 났다.
  - 768m: anon 662·678MB, peak 719·735MB, 상한 도달 0·0회. 예열된 실행에서 major GC 0회, 힙 사용 최대 229MB. 발급 요청 지연은 같은 범위다(예열 p50 3.3s, 쿠폰 행 갱신에서 줄을 선다, R11).
- 예외: auth도 `mem_limit: 768m`, 힙 40%(약 307MB)다(2026-10-30).
  - 계기: promotion을 잰 첫 실행(구매자 1,000명 로그인)에서 640m 상한에 23회 닿았다(anon 609MB).
  - 측정: k6 coupon(로그인 1,000건 뒤 동시 발급). 640m으로 빈 스택 두 번과 재사용 한 번, 768m으로 빈 스택 한 번과 재사용 한 번 돌렸다.
  - 640m: 상한 도달 0·0·96회(빈 스택마다 흔들린다). 96회 실행 뒤 쉬는 중에도 anon 624MB로 상한 16MB 앞이었고 페이지 캐시가 29MB까지 밀렸다. 커밋된 힙 217MB, 비힙 240MB(메타스페이스 161MB), 스레드 63개, 나머지 약 140MB가 네이티브다. 힙 사용은 최대 160MB라 힙이 아니라 힙 밖이 빠듯하다.
  - 768m: anon 601·608MB, peak 675·675MB, 상한 도달 0·0회. major GC 0회, 힙 사용 최대 157MB.
- 예외: file은 `mem_limit: 768m`, 힙 40%(약 307MB), `-Djdk.nio.maxCachedBufferSize=262144`다(2026-10-19, M2).
  - 측정: `apps` profile에서 6000x4000 JPEG(6MB) 10건을 gateway로 동시에 올리기를 3번 반복했다. cgroup `memory.stat`의 `anon`, `memory.peak`, `memory.swap.current`를 봤다.
  - 512m·힙 50%: 익명 메모리 450~500MB, 상한 도달 5,902회, 스왑 103MB. 스왑이 없는 환경이면 OOM으로 죽는다.
  - 원인(호스트 bootRun + Native Memory Tracking, 같은 힙 248MB): 부하에서 힙이 상한까지 커지고, 힙 밖이 약 230MB였다. 메타스페이스 100MB, Symbol·Class·Code·공유 클래스 74MB, 직접 버퍼(큰 `Files.write`의 스레드별 임시 버퍼) 35MB, NMT 밖 네이티브(JPEG 디코더 등) 약 37MB. `MALLOC_ARENA_MAX=2`는 차이가 없었다.
  - 768m·힙 50%도 힙이 384MB까지 커져 익명 메모리가 670MB, 스왑 21MB였다.
  - 768m·힙 40%·직접 버퍼 캐시 끔: 익명 메모리 608MB에서 멈췄고 스왑 0. `memory.peak`가 768MB에 닿는 것은 업로드 파일의 페이지 캐시이며 커널이 회수한다. 10건 동시 업로드는 0.8~2.9초였다.
  - 같은 측정에서 member·notification의 익명 메모리는 378~391MB로 M0 실측(230~335MB)보다 커졌다. M3 재측정 때 함께 본다.

M3 재측정(2026-10-21)
- 측정: E2E 스택(`openmarket-e2e`, 빈 볼륨)에서 M3 시나리오를 돈 뒤 `obs`·`search` profile을 더해 모든 profile을 띄웠다. gateway로 20,000건(동시 16: 주문 목록, 장바구니(order → catalog Feign), 상품 검색, 내 정보)을 보내고 cgroup `memory.stat`·`memory.peak`·`memory.events`와 `/actuator/prometheus`의 JVM 값을 봤다. 사용자당 레이트 리밋으로 order에 닿은 요청은 약 860건이었다.
- 결과(부하 뒤, 512m·힙 50% 기준)

  | 서비스 | anon | peak | 힙 사용 | 힙 밖(메타스페이스 등) |
  |---|---|---|---|---|
  | order | 496MB | 505MB | 111MB | 210MB |
  | gateway | 469MB | 489MB | 79MB | 140MB |
  | member | 460MB | 477MB | 98MB | 203MB |
  | auth | 447MB | 470MB | 95MB | 210MB |
  | catalog | 414MB | 433MB | 70MB | 187MB |
  | payment | 402MB | 425MB | 65MB | 183MB |
  | notification | 371MB | 399MB | 63MB | 172MB |
  | search | 346MB | 379MB | 66MB | 150MB |
  | discovery, config | 273MB, 241MB | 283MB, 253MB | 46MB, 43MB | 100MB, 84MB |
  | file(768m, 힙 40%) | 476MB | 517MB | 151MB | 188MB |

  - 메모리 상한 도달(`memory.events` max), 스왑, OOM은 없었다.
  - order의 확보된 힙은 134MB였다. 힙 밖 212MB와 JVM이 세지 않는 네이티브 약 150MB(스레드 46개, malloc 영역)가 더해져 anon이 496MB다. 힙이 상한 248MB까지 커지면 약 610MB로 512m을 넘는다. M0 실측(230~335MB)보다 커진 것은 Kafka 소비자, OpenFeign·Resilience4j, OAuth2 클라이언트, QueryDSL이 메타스페이스를 늘렸기 때문이다(M0 80~126MB → M3 180~210MB).
  - VM `free` 사용량은 14.4GB(모든 profile, 서비스 11개, 앱 3개는 멈춘 상태)다.
- 결정: Spring 서비스 `mem_limit`을 640m로 올리고 힙을 40%(256MB)로 한다. 힙 상한은 거의 그대로 두고, 늘어난 힙 밖과 네이티브의 여유를 준다. 최악의 경우 힙 256MB + 힙 밖 약 210MB + 네이티브 약 150MB로 640MB 안에 든다. file은 768m·힙 40% 그대로다.
  - 확인: 640m·힙 40%로 M3 E2E 전체를 돈 직후 order는 anon 463MB, peak 498MB(여유 약 140MB)였고 다른 서비스는 239~447MB였다. 상한 도달, 스왑, OOM은 없었다.
- 서비스 15개 전망(갱신): 상한 합계는 14 × 640MB + 768MB = 약 9.7GB에 나머지 약 4GB를 더해 약 13.7GB다. 실측 기준 VM은 지금 14.4GB에 서비스 4개(각 약 450MB)가 더해져 약 16.2GB다. 전체 재빌드를 함께 돌리면 20GB를 넘을 수 있어, `apps`를 띄운 채 전체 재빌드를 하지 않는다([dev-setup.md](../dev-setup.md) 2절).

M4 재측정(2026-10-22)
- 측정: E2E 스택(`openmarket-e2e`, 빈 볼륨, `apps`·`e2e` profile)에서 M4 시나리오 4개(주문·결제, 배송, 클레임, 환불·재시도, 인앱 알림, 이메일)를 돈 직후 cgroup `memory.stat`의 `anon`, `memory.peak`, `memory.events`를 봤다. 관측성 profile과 부하 도구는 띄우지 않았다.
- 결과

  | 서비스 | anon | peak | 상한 |
  |---|---|---|---|
  | notification | 505MB | 564MB | 640MB |
  | order | 480MB | 507MB | 640MB |
  | auth | 455MB | 514MB | 640MB |
  | fulfillment | 446MB | 466MB | 640MB |
  | member | 444MB | 499MB | 640MB |
  | payment | 429MB | 482MB | 640MB |
  | catalog | 421MB | 474MB | 640MB |
  | gateway, search | 341MB, 337MB | 368MB, 387MB | 640MB |
  | discovery, config | 281MB, 231MB | 440MB, 268MB | 640MB |
  | file | 567MB | 628MB | 768MB |

  - Spring 서비스와 file은 상한 도달(`memory.events` max)과 OOM이 없었다. kafka-ui는 상한 도달이 236회였지만 anon이 292MB로 페이지 캐시 회수다(OOM 없음).
  - notification이 가장 상한에 가깝다(여유 76MB). M4에 Thymeleaf 메일, OpenFeign(member 연락처), 리스너 6개가 더해졌다.
  - VM `free` 사용량은 12.6GB(앱 3개는 멈춘 상태, `obs`·`search` 일부 없음)다.
- 결정: 상한은 그대로 둔다. notification은 M5에서 리뷰·정산 알림을 더할 때 다시 잰다. 서비스 15개 재측정은 M5 마감 전에 한다.

M5 중간 측정(2026-10-25)
- 측정: 개발용 스택(`openmarket`, 기존 볼륨)에 `apps` profile로 Spring 서비스 15개를 모두 띄웠다(settlement 골격 PR). 관측성 profile, 앱 3개, 부하 도구는 띄우지 않았다. 연결 수 문제(아래)를 고친 뒤 서비스들을 다시 시작하고 몇 분 기다린 대기 상태에서 cgroup `memory.stat`의 `anon`, `memory.peak`, `memory.events`를 봤다. 시나리오를 돌린 뒤가 아니라서 peak는 M4보다 낮게 나온다.
- 결과

  | 서비스 | anon | peak | 상한 |
  |---|---|---|---|
  | notification | 430MB | 470MB | 640MB |
  | payment, order | 427MB, 427MB | 464MB, 448MB | 640MB |
  | auth, promotion, member | 423MB, 421MB, 420MB | 449MB, 445MB, 431MB | 640MB |
  | fulfillment, catalog | 411MB, 401MB | 420MB, 411MB | 640MB |
  | settlement, review | 396MB, 393MB | 413MB, 400MB | 640MB |
  | search, gateway | 335MB, 318MB | 378MB, 344MB | 640MB |
  | discovery, config | 264MB, 234MB | 271MB, 252MB | 640MB |
  | file | 400MB | 437MB | 768MB |
  | elasticsearch, connect, kafka | 1,382MB, 463MB, 420MB | 1,633MB, 729MB, 981MB | 없음 |

  - 모든 컨테이너에서 상한 도달(`memory.events` max)과 OOM이 없었다.
  - 컨테이너 anon 합계는 8.4GB, VM `free` 사용량은 14.5GB(20GB 중, 사용 가능 5.5GB)다. M3 전망(서비스 15개, 약 16.2GB)보다 작다.
  - 새 서비스(promotion, review, settlement)는 대기 상태에서 393~421MB로 기존 서비스와 비슷하다.
- 같이 찾은 것: Postgres 연결 수. DB를 쓰는 서비스 12개가 Hikari 기본 풀(10)을 쓰면 합계 120으로 `max_connections` 100을 넘는다. settlement를 더하자 마지막에 뜬 search가 연결을 받지 못해 뜨지 않았고, Debezium 커넥터도 등록에 실패해 connect가 멈췄다. 공통 설정에서 서비스당 풀을 5(합계 60)로 정했다([data.md](data.md) "연결 수").
- 결정: 메모리 상한은 그대로 둔다. 시나리오를 돈 뒤의 재측정은 M5 마감(완료 기준 E2E) 때 한다.

M5 재측정(2026-10-26)
- 측정: E2E 스택(`openmarket-e2e`, 빈 볼륨, `apps`·`e2e` profile, Spring 서비스 15개)에서 M5 시나리오 3개(쿠폰 주문, 리뷰·검색·답글·숨김·수수료율, 완료 기준: 정산 확정 두 번·지급)를 `E2E_KEEP=1`로 돈 직후 cgroup `memory.stat`의 `anon`, `memory.peak`, `memory.events`를 봤다. 관측성 profile과 부하 도구는 띄우지 않았고, 앱 3개는 run.sh가 멈춘 뒤다.
- 결과

  | 서비스 | anon | peak | 상한 |
  |---|---|---|---|
  | notification | 502MB | 534MB | 640MB |
  | order, auth | 467MB, 461MB | 486MB, 497MB | 640MB |
  | member, promotion | 449MB, 441MB | 487MB, 478MB | 640MB |
  | fulfillment, catalog, payment | 429MB, 423MB, 419MB | 441MB, 466MB, 454MB | 640MB |
  | review, settlement | 418MB, 412MB | 433MB, 419MB | 640MB |
  | search, gateway | 346MB, 337MB | 383MB, 365MB | 640MB |
  | discovery, config | 271MB, 229MB | 325MB, 263MB | 640MB |
  | file | 496MB | 528MB | 768MB |
  | elasticsearch, connect, kafka | 1,487MB, 519MB, 430MB | 1,626MB, 902MB, 660MB | 없음 |

  - 컨테이너 23개 모두 상한 도달(`memory.events` max)과 OOM이 0이다(kafka-ui도 0).
  - notification은 M5에서 리스너 셋(리뷰, 정산 확정, 정산 지급)이 더해졌지만 peak 534MB로 M4(564MB)보다 낮다. 여유 106MB다. M4 측정은 시나리오 4개(메일 다수)를 돈 뒤라 메일 렌더링이 더 많았다.
  - 새 서비스(promotion, review, settlement)는 시나리오 뒤에도 412~441MB로 기존 서비스와 같은 수준이다.
  - 컨테이너 anon 합계 9.0GB, VM `free` 사용량 12.8GB(20GB 중, 사용 가능 7.2GB)다. 스택을 지운 뒤 3.6GB였다. M3 전망(서비스 15개, 약 16.2GB)보다 작다.
- 결정: 상한은 그대로 둔다(Spring 640m·힙 40%, file 768m). `.wslconfig` 20GB 권장도 그대로다. 부하 중 측정은 M6 부하 테스트(k6)에서 한다.

서비스 15개 전망(M0, 512m 기준. M3 갱신은 위 "M3 재측정")
- 컨테이너: 실측 최댓값(335MB) 기준 Spring 서비스 15개는 약 5GB, 나머지 12개는 약 4GB다. 합계 약 9GB. 상한 기준은 15 × 512MB = 7.5GB에 4GB를 더해 약 11.5GB다.
- VM 전체: 컨테이너 9GB + Docker·WSL 2GB = 약 11GB. 전체 재빌드를 함께 돌리면 4.6GB가 더해져 약 15.6GB, 컨테이너가 상한까지 차면 약 18GB다.
- 그래서 `.wslconfig`는 20GB를 권장한다.
- M3에 서비스에 Kafka 소비자와 데이터가 붙어 실제 사용량이 이 전망보다 컸다(서비스당 240~500MB). 다음 재측정은 서비스가 15개가 되는 M5 마감 전에 한다. 부족하면 E2E를 범위별 profile 조합으로 나눠 돌린다.

## 부하 측정 (R13, M6)
환경(2026-10-27): `infra/k6/run.sh`가 빈 볼륨의 별도 Compose 프로젝트(`openmarket-load`, 기본·`apps`·`search`·`obs` profile, 서비스 15개)를 띄우고 k6 2.3.0 컨테이너로 gateway를 불렀다. Docker Desktop(WSL2), VM 메모리 20GB, CPU 6개. 시나리오와 실행 방법은 [dev-setup.md](../dev-setup.md) 9절이다. 숫자는 30초 예열 뒤 측정 단계(2분)의 값이다.

| 시나리오 | 부하 | p50 | p95 | p99 | 실패 | 목표(NFR-9) |
|---|---|---|---|---|---|---|
| 상품 상세 | 초당 75건 | 6.8ms | 14.4ms | 30.8ms | 0% | p95 200ms 이하, 만족 |
| 검색 | 초당 25건 | 8.1ms | 19.0ms | 34.4ms | 0% | p95 200ms 이하, 만족 |
| 주문 생성(PG 제외) | 초당 10건, 구매자 50명 | 30.0ms | 51.6ms | 76.8ms | 0% | p95 500ms 이하, 만족 |
| 선착순 쿠폰 | 수량 100장에 1,000명 동시 | 4.5s | 5.7s | 5.8s | - | 초과 발급 0건, 만족(발급 100, 품절 900, 그 밖의 응답 0, DB 발급 수 100) |

부하를 올린 측정(같은 스택, 예열됨)
| 시나리오 | 부하 | p95 | 실패 | 관찰 |
|---|---|---|---|---|
| 조회·검색 | 초당 100건 2회 더 | 상세 10.8·9.9ms, 검색 12.6·10.9ms | 0% | 첫 측정보다 낮다. 첫 측정은 스택이 뜬 직후였다 |
| 조회·검색 | 초당 300건 | 상세 33.4ms, 검색 25.0ms | 0% | catalog 연결 풀 사용 최대 3/5, 대기 0 |
| 조회·검색 | 초당 600건 | 상세 851ms, 검색 411ms | 0% | 목표 초과. 보내지 못한 요청 3,649건. catalog 풀 대기 최대 81, CPU 평균 94%(한 코어), 메모리 상한 도달. 전체 CPU 평균 460%/600% |
| 주문 생성 | 초당 30건 | 50.6ms | 0% | order 풀 사용 최대 3/5, 대기 0 |

찾은 것
- 조회·검색은 초당 300건까지 목표의 1/6 안쪽이고, 300~600건 사이에서 catalog가 먼저 한계에 닿는다. 상품 상세가 요청마다 DB를 읽는데 풀(5)과 CPU가 모자라다. 개선 후보다(성능 개선 PR).
- 선착순 쿠폰은 정합성은 맞지만 1,000건이 한꺼번에 오면 응답이 4~6초 걸린다. 품절 응답(Redis에서 끝난다)도 같이 늦어 DB가 아니라 앞단(gateway → promotion 연결, 레이트 리밋의 Redis 호출, promotion 스레드)에서 줄을 서는 것으로 보인다. 원인을 성능 개선 PR에서 찾는다. 막 뜬 스택에서 coupon만 돌리면 발급 게이트(Redis)를 제때 부르지 못해 503 `PROMOTION_UNAVAILABLE`이 20건, 60초 시간 초과가 1건 나왔다(2026-10-27). 예열된 스택에서는 0건이다. coupon.js는 그 밖의 응답을 상태·오류 코드별로 세어 로그에 남긴다.
- M6 마감 재측정(2026-10-29)에서 쿠폰 503이 예열된 스택에서도 232건(그 밖에 본문 없는 500 10건. 스택을 지운 뒤라 원인을 보지 못했고, 뒤의 세 번에는 없었다), 새 스택에서 125건으로 늘었다. 초과 발급은 0건이었다. promotion 로그의 원인은 모두 Redis 명령 시간 초과(500ms, 대부분 `EVALSHA`)였지만 Redis SLOWLOG(10ms 기준)는 비어 있었다. 트레이스에서는 캐시 조회밖에 없는 `evalsha` 앞 구간이 216ms, 응답 뒤 구간이 1.5초 비었다. 버스트 순간 promotion은 힙 231/256MB에서 major GC 283ms, CPU 254%, 호스트 CPU 93%였다. Redis가 아니라 promotion 클라이언트 쪽 지연이다. 끊긴 연결은 Redis 장애 수정(아래 "장애 주입")부터 바로 거절하므로 시간 초과는 연결된 Redis가 응답하지 않을 때만 쓰인다. 2초로 올리자 두 번 모두 그 밖의 응답 0건, 발급 100, 품절 900이었다. 그때 promotion의 `EVALSHA` 최대 1.41s, major GC 최대 410ms였다([promotion.md](promotion.md) "선착순 발급"). 빈 스택에서 browse → order → coupon을 다시 돌려 쿠폰은 그 밖의 응답 0건, 주문 생성 p95 80ms였다. 같은 실행의 browse는 p95가 상세 326ms, 검색 283ms로 목표를 넘었다(중앙값 7ms, 풀 대기 0). 같은 코드로 browse만 다시 재니 17.2ms, 23.1ms였다. 같은 날 세 번의 상세 p95가 50, 326, 17ms로, 이 호스트(CPU 6개에 서비스 15개와 관측성 스택)에서는 꼬리 지연이 실행마다 크게 흔들린다.
- 2026-10-30 auth 메모리 측정의 빈 스택 세 번 가운데 한 번에서 503 `PROMOTION_UNAVAILABLE`이 9건 나왔다(1,000건 중, 예열된 실행은 0건, 초과 발급 0). 설계대로 Redis를 부르지 못하면 막는 실패다. 그 실행은 스택을 지운 뒤라 원인을 보지 못했고, 스택을 남긴 다음 빈 스택 실행에서는 나오지 않았다. 막 뜬 스택의 첫 버스트에서 드물게 남는 것으로 보고 열어 둔다.
- auth 연결 풀 교착: 로그인이 동시에 5건을 넘으면 `/oauth2/token`·`/oauth2/authorize`가 30초 기다린 뒤 500(`CannotGetJdbcConnectionException`)이 됐다. `JdbcOAuth2AuthorizationService`가 트랜잭션 없이 인가 기록을 읽는 중에(결과 집합을 연 채) `JdbcRegisteredClientRepository`로 두 번째 연결을 받는다. 동시 요청이 풀 크기를 넘으면 모두가 첫 연결을 쥔 채 둘째를 기다린다(연결 사용 시간 최대 30.04s, 시간 초과 약 45건). 로그인이 몰리면(이벤트 시작) 실제로 생기는 결함이다. 고침(2026-10-27): 인가·동의 저장소 호출을 트랜잭션으로 감싸 두 조회가 같은 연결을 쓰게 했다(`TransactionalAuthorizationService`). 풀을 1개로 둔 통합 테스트가 재현하고 막는다. k6는 로그인도 50건씩 동시에 보낸다. 고친 뒤 같은 순서(browse → order → coupon)로 다시 재니 구매자 1,000명 로그인에 500이 없었다(auth 풀 대기 최대 45, 줄을 섰다가 처리됨). 다른 숫자는 첫 측정과 같은 범위였다(상세 p95 18.1ms, 검색 22.9ms, 주문 57.5ms, 쿠폰 발급 100·품절 900·그 밖 0, 발급 요청 p95 6.4s).
- 기동 직후 예열: 예열 없이 잰 첫 측정의 p95는 상세 255ms, 검색 231ms였고 catalog 풀 대기도 3이었다. 예열 뒤에는 10~19ms다. 배포 직후 짧은 구간의 지연이다.
- 시드 중 찾은 것: 이메일 인증 직후 member가 아직 인증을 모르는 구간(이벤트 지연)에 입점 신청이 422 `MEMBER_EMAIL_NOT_VERIFIED`였다. 설계대로의 최종 일관성이고, 화면에서는 인증 뒤 로그인 단계를 거쳐 겪기 어렵다. 시드는 다시 시도한다.

### 성능 개선 (2026-10-27, M6)
같은 환경에서 빈 스택을 띄우고 browse → order → coupon → 조회·검색 초당 600건 순서로 쟀다(개선 전, 개선 후 1·2를 같은 순서로). 원인은 Tempo 트레이스와 Prometheus 지표로 찾았다(모든 요청을 샘플링한다). 결과 표는 [README](../../README.md) "성능 개선 기록"이다.

| 측정 | 개선 전 | 개선 후 1(카테고리·쿠폰 캐시) | 개선 후 2(+ 쿠폰 캐시 새로 읽기, ParallelGC) |
|---|---|---|---|
| 기본 부하 상세 / 검색 p95 | 31.1 / 36.9ms | 14.2 / 20.1ms | 11.7 / 16.9ms |
| 초당 600건 상세 / 검색 p95 | 1,425 / 1,358ms | 1,204 / 1,054ms | 371 / 276ms |
| 초당 600건 보내지 못한 요청 | 22,861 | 8,599 | 2,144 |
| 주문 생성 p95 | 65.6ms | 57.6ms | 53.4ms |
| 쿠폰 발급 p50 / p95 | 5.6 / 7.5s | 4.2 / 4.4s | 2.6 / 3.7s |

- catalog 상품 상세: 카테고리 경로를 단계마다 따로 읽어 요청 하나가 DB를 5~6번 왕복했다. 카테고리는 마이그레이션으로만 바뀌므로 처음 쓸 때 한 번 읽어 둔다([catalog.md](catalog.md) "카테고리").
- 쿠폰 발급: 트레이스에서 promotion의 Redis 호출은 1~16ms인데 그 앞뒤가 3초씩 비었다. 연결 획득 시간이 최대 6.47s였다. 요청마다 쿠폰을 DB에서 읽느라 발급 저장 트랜잭션(쿠폰 행 갱신으로 줄을 선다)과 연결 5개를 다퉜다. 발급용 쿠폰 조회를 메모리에 둔다([promotion.md](promotion.md) "선착순 발급"). 처음에는 1초 뒤 만료로 했는데, 만료 순간 다시 읽는 요청이 저장 트랜잭션 뒤에 줄을 서며 같은 쿠폰의 모든 요청이 2초씩 멈췄다. 지금 값을 주면서 뒤에서 새로 읽게 바꿨다.
- GC: 640m 컨테이너에서 JVM이 SerialGC를 고른다. 초당 600건 구간에서 gateway는 279번, 합계 16.5s(평균 59ms) 멈췄다. gateway·catalog만 ParallelGC로 바꾸자 같은 구간 합계가 gateway 5.4s, catalog 3.5s로 줄었다. 대신 초당 600건에서 메모리 peak가 catalog 613 → 637MB, gateway 552 → 582MB로 늘었다(상한 도달·재시작 없음). catalog는 상한까지 여유가 3MB라 이 부하에서는 빠듯하다(R6).
- 남은 것(후속)
  - 쿠폰 발급 성공 100건은 쿠폰 행의 발급 수 갱신에서 줄을 서서 3초 안팎 걸린다. 수량을 DB가 마지막으로 지키는 설계의 비용이다(R11).
  - 품절 응답의 남은 1~2초는 gateway 레이트 리밋의 Redis 호출이다(한 번에 최대 875ms). Redis 서버는 스크립트당 평균 51µs라 gateway 클라이언트 쪽이다. 몰릴 때 명령 시간 초과(200ms)가 858건 났고, 이때 레이트 리미터는 요청을 통과시킨다.
    - 고침(2026-10-29): Lettuce는 기본값으로 응답 신호를 자기 I/O 스레드에서 보낸다. 그래서 레이트 리밋 다음의 필터 체인(JWT 검증, 라우팅)이 모두 그 스레드에서 돌아, 버스트 때 다른 Redis 응답 해석이 밀렸다. 테스트 필터로 기록하니 라우팅 직전 스레드가 `lettuce-epollEventLoop`였다. `publishOnScheduler(true)`로 신호를 스케줄러로 넘긴다(`RedisPublishThreadTest`가 막는다). 같은 스택에서 coupon을 고치기 전 두 번(빈 스택, 예열), 고친 뒤 두 번(gateway 재시작 직후, 예열) 쟀다. gateway Redis 시간 초과 1,266·953건 → 0·0건, 예열된 실행끼리 전체 p95 3.86s → 3.24s, 발급 요청 p50 3.18s → 2.60s. 발급 100·품절 900·그 밖의 응답 0은 네 번 모두 같았다. 막 뜬(재시작한) 스택의 첫 실행은 고치기 전후 모두 발급 p50 6~7초로 느렸다. 60초 무응답은 네 번 모두 없었지만 원래 드물어 고쳤다고 보지 않는다.
  - 드물게 gateway가 요청을 서비스로 보내지 않은 채 60초 동안 응답하지 않는다(개선 전에도 측정마다 0~1건). 트레이스는 레이트 리밋 직후나 그 전에서 멈추고 오류 로그가 없다. gateway 응답 시간 제한(10s)은 서비스 호출에만 걸린다. 2026-10-29 coupon 여섯 번(Lettuce 신호 수정 전 2, 후 4)에서는 60초 무응답도, 본문 없는 500도 없었다. 레이트 리밋 다음 체인이 Lettuce I/O 스레드에서 돌던 것과 관련이 있을 수 있지만 재현하지 못해 열어 둔다.

## 장애 주입 (R16, M6)
환경(2026-10-27): `infra/fault/run.sh`가 빈 볼륨의 별도 Compose 프로젝트(`openmarket-fault`, 기본·`apps`·`e2e`·`fault` profile, 서비스 15개)를 띄운다. 서비스의 DB 연결은 Toxiproxy 2.12.0을 거치고, 토스 대신 가짜 PG를 쓴다. 결제 기한은 4분이다. 실행 방법과 검사 항목은 [dev-setup.md](../dev-setup.md) 10절이다.

장애마다 k6 `saga.js`가 초당 2건, 3분 동안 주문 생성 → 결제 준비 대기 → 가짜 PG 승인 → 결제 승인을 흘렸다. 장애는 시작 60초 뒤 60초 동안 넣었다(pg는 내내). 실패한 요청은 같은 멱등 키로 다시 보냈다.

| 장애 | 주문 | 결과 | 반복 하나의 시간(p95) | 위반 | DLT |
|---|---|---|---|---|---|
| `kafka`: 브로커 중지 1분 | 361 | 모두 결제 완료 | 73s(결제 준비가 Kafka 복구를 기다렸다) | 0 | 0 |
| `db`: 서비스 → Postgres 300ms ± 100ms 1분 | 360 | 모두 결제 완료 | 55s | 0 | 0 |
| `db-down`: 서비스 → Postgres 끊김 1분 | 360 | 모두 결제 완료 | 65s | 0 | 0 |
| `pg`: 세 번째 주문마다 승인 응답 15초 지연 | 361 | 모두 결제 완료 | 15s(payment 읽기 타임아웃 10초 뒤 조회로 정했다) | 0 | 0 |

- 검사(`infra/fault/check.sql`)는 끝나지 않은 주문, 결제 완료 ↔ 승인된 결제, 승인된 결제 ↔ 결제 완료 주문, 정해지지 않은 결제, 결제 완료 ↔ 확정된 재고 예약, 취소 ↔ 풀린 예약, 남은 재고 예약, SKU별 재고 차감(지금 재고 + 확정 수량 - 조정 = 초기 재고)이다. 모두 0이었다.
- 찾은 것: DB가 느리거나 끊기면 주문 생성이 gateway 타임아웃(10초)을 넘긴다. 서비스는 요청을 계속 처리해 주문을 만들고, 같은 멱등 키의 재요청은 처리가 끝날 때까지 409 `COMMON_IDEMPOTENCY_IN_PROGRESS`, 끝난 뒤에는 처음 응답(201)이다. 첫 실행에서 k6가 이 409를 거절로 보고 멈추자 그 주문 96건(db 39, db-down 59 중)이 결제되지 않은 채 결제 기한 뒤 `PAYMENT_TIMEOUT`으로 취소됐고, 결제(`READY` → `CANCELLED`)와 재고 예약(`RELEASED`)도 맞게 풀렸다. 설계대로이고 데이터는 맞다. shop 주문서는 같은 키를 폼에 두므로 사용자가 다시 누르면 처음 결과를 받는다. k6는 이 409를 다시 부르도록 고쳤고, 다시 잰 결과가 위 표다.

### 쿠폰 조합, Redis, 복제 슬롯 (2026-10-27)
| 장애 | 시나리오 | 결과 | 위반 | DLT |
|---|---|---|---|---|
| `coupon`: 쿠폰 예약 실패 조합(ADR 0021) | `coupon-saga.js` 30경우: 같은 쿠폰으로 두 주문, 재고 1개 상품에 두 구매자, 같은 쿠폰으로 재고 1개 상품에 두 주문. 두 주문을 한꺼번에 넣는다 | 모든 경우 한 주문만 결제(두 주문 모두 결제 0). 진 주문은 `COUPON_UNAVAILABLE` 15, `STOCK_UNAVAILABLE` 15로 취소. 재고 예약 확정 30·실패 20·보상 해제 10, 발급 쿠폰 사용 30·복원 30 | 0 | 0 |
| `redis`: 발급 중 Redis 1분 죽임(SIGKILL) | `coupon-issue.js` 초당 3명, 503이면 1초 뒤 다시 | 죽은 동안 503 `PROMOTION_UNAVAILABLE` 2,621건, DB만으로 발급하지 않았다. 다시 뜬 뒤 발급이 이어졌고 DB 발급 수 = 발급 쿠폰 행 수 ≤ 수량 | 0 | 0 |
| `slot`: Debezium 중지 + WAL 200MB로 슬롯 무효(이 스택은 상한 64MB) | `saga.js` | 운영 절차(슬롯 삭제) 뒤 주문 361건 모두 결제 완료. 슬롯이 끊긴 동안 결제 준비가 밀려 반복 하나 p95 2분 | 0 | 0 |

찾은 것
- 복제 슬롯 무효는 자동으로 복구되지 않는다(R5 위 "슬롯 무효"). 운영 절차 없이 둔 첫 실행에서는 주문 275건이 결제 준비를 받지 못한 채 만료됐고, 그 취소 이벤트도 나가지 않아 잡힌 재고 예약·승인된 결제가 남았다(위반 5종). 슬롯을 지우자 스냅샷으로 모두 따라잡아 위반이 0이 됐다. `run.sh`의 `slot`은 이 절차를 그대로 실행한다. 슬롯 무효를 알리는 경보는 대시보드·알림, 절차는 [런북](../runbook.md) "복제 슬롯 무효"에 있다. 경보 확인(2026-10-29): `slot`에 관측성 스택을 더해 돌리자 슬롯이 `lost`가 된 뒤 75초 만에 Grafana "복제 슬롯 무효" 메일이 Mailpit에 왔고, 슬롯을 지운 뒤 해결 메일이 왔다. 주문 361건 모두 결제 완료, 위반 0, DLT 0([observability.md](observability.md) "대시보드와 알림").
- Redis 장애 중 gateway OOM(결함): Redis를 죽인 채 gateway에 동시 요청 50개를 계속 보내자 레이트 리밋의 Redis 시간 초과가 6,444건 쌓이고 gateway가 `OutOfMemoryError: GC overhead limit exceeded`를 냈다. 요청 7,907건 중 5건이 70초 동안 응답이 없었고 24초 걸린 요청과 504도 있었다. 요청을 하나씩 보낼 때는 0.29초에 통과했다(레이트 리밋은 실패하면 통과시킨다). k6 첫 실행에서 발급 요청 246건이 60초 동안 응답을 받지 못한 것과, 성능 개선의 "드문 60초 무응답"(위 "부하 측정")도 같은 원인일 수 있다. 고침(2026-10-27): 원인은 Lettuce 기본값이다. 연결이 끊긴 동안 받은 명령을 무제한 버퍼에 쌓았다가 다시 연결되면 보낸다(Lettuce FAQ "Excessive Memory Usage while disconnected"). gateway·auth·promotion에 끊긴 동안 명령을 바로 거부하고(`REJECT_COMMANDS`) 응답 대기 명령을 연결마다 1,024개로 묶는 옵션을 뒀다([data.md](data.md) "캐시와 기타 저장소"). 컨테이너를 멈춘 재현 테스트(`RedisDisconnectTest`: 요청 20개 6.0s → 1s 안, `RedisDisconnectIntegrationTest`: 차지 10번 5.1s → 1s 안)가 막는다. 같은 동시 요청 50개 시험에서 요청 18,583건 중 1초를 넘은 것 0(최대 0.9s), OOM 0이었다. 200이 아닌 응답은 Redis가 살아 있는 동안의 429(한 IP에서 동시 50개)뿐이다.
- Redis가 돌아오는 순간 promotion 지연(결함): Redis를 다시 띄운 직후 발급 요청 12건이 gateway 응답 시간 제한(10초)을 넘겨 504였다. Redis 명령 시간 초과(500ms)보다 훨씬 길다. 끊긴 동안 쌓인 차지 스크립트를 다시 연결되는 순간 한꺼번에 보낸 것이다(호출자에게 이미 503을 준 차지가 나중에 실행되기도 했다). 위와 같이 고쳤다. 고친 뒤 `redis` 장애에서 그 밖의 응답(504, 무응답) 0, 구매자 541명 모두 발급, 503 6,035건(바로 실패해 더 자주 다시 눌렀다), 위반 0, DLT 0이었다.

