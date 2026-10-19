# 기술 리스크

마일스톤 열의 시점에 확인한다. 확인하면 결과와 날짜를 적고, 결정이 생기면 ADR로 남긴다.

| # | 리스크 | 왜 문제인가 | 확인 방법 | 마일스톤 | 상태 |
|---|---|---|---|---|---|
| R1 | Java 25 + Spring Boot 4 + Spring Cloud 호환 조합 | Spring Cloud 릴리스 트레인이 쓰려는 Boot 버전을 지원해야 한다 | 공식 호환표와 start.spring.io 메타데이터로 확인하고, 빈 서비스를 빌드·기동한다 | M0-b | 확인(2026-10-16): Boot 4.1.1 + Cloud 2025.1.3, Java 25 지원. [ADR 0009](../adr/0009-version-matrix.md) |
| R2 | QueryDSL APT와 Java 25, Hibernate 7 | 원본 QueryDSL은 유지보수가 느리다. 포크(OpenFeign) 버전의 Jakarta·JDK 지원을 확인해야 한다 | 템플릿 서비스에서 Q-타입 생성과 조회 테스트 | M0-b | 확인(2026-10-16): QueryDSL 7.7 + `annotationProcessor` 경로로 Java 25에서 Q-타입 생성. `ConventionPluginsTest` |
| R3 | 포매터·정적 분석 도구의 Java 25 지원 | 도구가 새 문법을 못 읽으면 빌드가 깨진다 | 후보 도구로 템플릿 빌드 | M0-b | 확인(2026-10-16): palantir 제외, google-java-format 1.36.1 + Error Prone + NullAway 동작. [ADR 0010](../adr/0010-code-style-tools.md) |
| R4 | Gateway 구현 선택(WebFlux 또는 WebMVC) | 레이트 리밋, 토큰 처리 필터 지원 범위가 다르다 | 두 구현의 필터 문서 비교, 필요한 필터(JWT, Redis 레이트 리밋, 경로 재작성) 동작 확인 | M0-c | 확인(2026-10-16): WebFlux 선택. Redis 레이트 리밋을 설정으로 쓸 수 있다(WebMVC는 Bucket4j, 키 리졸버는 Java DSL만). JWT·역할·레이트 리밋·접두어 제거를 `GatewayTest`, `RateLimitTest`로 확인. Redis 무응답 시 기본 타임아웃(60s)만큼 요청이 멈추는 것을 발견해 200ms로 줄였다(`RedisOutageTest`). [ADR 0011](../adr/0011-gateway-webflux.md) |
| R5 | Debezium과 Postgres 논리 복제 | 스키마 여러 개의 outbox를 커넥터 하나로 읽고, 토픽을 `aggregate_type`으로 나눠야 한다. 복제 슬롯이 쌓이면 디스크가 찬다 | 샘플 서비스 두 개로 outbox → 토픽 확인, 커넥터 중지 후 재기동 시 유실 없는지 확인 | M0-d | 확인(2026-10-18): 스키마 두 개(member, catalog)의 outbox가 커넥터 하나로 각 토픽에 나가고, 커넥터·Kafka 중지 중에 쓴 행은 재기동 뒤 유실 없이 나왔다. heartbeat가 없으면 슬롯이 따라오지 못하고, 없는 토픽에서는 태스크가 RUNNING인 채 멈추며, Kafka 재접속 뒤 5분간 태스크를 다시 받지 못하는 것을 발견해 설정으로 고쳤다. 아래 "outbox 발행 확인" |
| R6 | 로컬 메모리 예산 | 서비스 15개, Kafka, Connect, ES, 관측성을 함께 띄워야 한다. WSL 메모리가 부족하면 E2E를 돌릴 수 없다 | profile별로 띄워 `docker stats`로 실측하고, 서비스 JVM 힙 기본값(`-XX:MaxRAMPercentage`)을 정한다 | M0-c | 확인(2026-10-17): 전체 profile 실측 5.1GB(Spring 서비스 4개). 상한이 없으면 JVM이 VM 메모리 기준으로 힙 상한을 5GB로 잡는 것을 발견했다. JVM 컨테이너에 `mem_limit` 512m + `MaxRAMPercentage=50`을 두었다. 서비스 15개 기준 컨테이너 상한 합계 약 11.5GB, 빌드까지 VM 전체 약 18GB로 `.wslconfig` 20GB를 권장한다. 아래 "메모리 예산". 서비스에 Kafka 소비자와 데이터가 붙는 M3 마감 전에 다시 잰다 |
| R7 | auth issuer 호스트 이름 | 브라우저, IDE 실행 서비스, 컨테이너가 같은 issuer URL로 auth에 닿아야 한다 | hosts + 네트워크 별칭으로 세 경우 모두 토큰 검증 확인 | M1 | 확인(2026-10-18): issuer를 고정하면 요청 주소와 상관없이 `iss`가 같다(`AuthorizationServerIntegrationTest`, 무작위 포트·다른 Host). 호스트(curl, hosts 대신 `--resolve`)에서 PKCE 로그인으로 받은 토큰을 컨테이너 gateway가 검증했다. 네트워크 별칭은 auth를 IDE로 띄우면 사라져 JWK를 받지 못해, 컨테이너가 `host-gateway`로 호스트 포트에 닿게 바꿨다. auth가 컨테이너일 때와 IDE일 때 모두 검증 통과. IDE gateway(bootRun)도 hosts 항목을 넣은 뒤 같은 결과였다(유효 토큰 통과, 서명 위조·토큰 없음 401). IDE gateway는 Netty DNS 리졸버가 `/etc/hosts`만 읽어서, JVM 옵션 `jdk.net.hosts.file`로는 대신할 수 없다. [ADR 0013](../adr/0013-auth-issuer-host.md) |
| R8 | Next.js BFF 세션과 토큰 갱신 | 서버 컴포넌트, Server Action, Route Handler 모두에서 세션을 읽고 토큰을 갱신해야 한다. 동시 갱신 경쟁이 생길 수 있다 | 갱신 중복 방지(Redis 락) 시나리오 테스트 | M1 | 확인(2026-10-18): Redis 락(`SET NX PX`)을 잡은 한 요청만 갱신하고 나머지는 기다린다. BFF 인스턴스 둘이 동시에 20번 갱신해도 auth 요청은 1번이다(`bff.test.ts`, 락을 끄면 2번으로 실패). 실제 auth와 브라우저에서 액세스 토큰을 강제 만료한 뒤 서버 컴포넌트 5·Route Handler 5·Server Action 1을 동시에 보내기를 3번 반복했다. 모두 인증됐고 세션이 유지됐다. [ADR 0014](../adr/0014-bff-session.md) |
| R9 | 토스페이먼츠 테스트 환경 | 테스트 키로 승인, 부분취소, 웹훅을 로컬에서 받을 수 있어야 한다. 웹훅은 공인 주소가 필요하다 | 승인·취소 API 확인. 웹훅은 결제 조회 폴링으로 대신할 수 있는지 확인 | M3 | 미확인 |
| R10 | Elasticsearch nori 플러그인 | 공식 이미지에 nori가 없어서 커스텀 이미지가 필요하다 | Dockerfile로 플러그인 설치, 인덱스 분석기 테스트 | M2 | 확인(2026-10-19): 공식 9.4.6 이미지에 `analysis-nori` 9.4.6을 설치한 이미지가 뜨고, 메모리는 1.5GB로 같다. 색인·검색 `decompound_mode` 조합 셋을 상품명으로 비교했다. 검색도 `mixed`로 해야 붙여 쓴 원형(`사과즙`)이 띄어 쓴 문서보다 위로 오고, `operator: and`에서도 띄어 쓴 문서를 놓치지 않았다. [ADR 0016](../adr/0016-elasticsearch-nori.md) |

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

확인하지 않은 것: 슬롯이 `max_slot_wal_keep_size`(1GB)를 넘어 무효가 되는 경우. 이때 `when_needed`가 스냅샷을 다시 찍는다(위 스냅샷 경로와 같다). M6 장애 주입에서 확인한다.

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
  - 2GB 미만 컨테이너라 JVM이 SerialGC를 고른다. 20,000건에 minor GC 41회, 합계 0.2s로 이 크기 힙에는 충분하다. 바꿀 필요가 생기면 서비스별로 `JDK_JAVA_OPTIONS`를 덮어쓴다.
- Kafka, Connect, Elasticsearch는 이미지 설정으로 힙을 고정했으므로 `mem_limit`을 두지 않는다. 나머지는 사용량이 작아 두지 않는다.
- 예외: file은 `mem_limit: 768m`, 힙 40%(약 307MB), `-Djdk.nio.maxCachedBufferSize=262144`다(2026-10-19, M2).
  - 측정: `apps` profile에서 6000x4000 JPEG(6MB) 10건을 gateway로 동시에 올리기를 3번 반복했다. cgroup `memory.stat`의 `anon`, `memory.peak`, `memory.swap.current`를 봤다.
  - 512m·힙 50%: 익명 메모리 450~500MB, 상한 도달 5,902회, 스왑 103MB. 스왑이 없는 환경이면 OOM으로 죽는다.
  - 원인(호스트 bootRun + Native Memory Tracking, 같은 힙 248MB): 부하에서 힙이 상한까지 커지고, 힙 밖이 약 230MB였다. 메타스페이스 100MB, Symbol·Class·Code·공유 클래스 74MB, 직접 버퍼(큰 `Files.write`의 스레드별 임시 버퍼) 35MB, NMT 밖 네이티브(JPEG 디코더 등) 약 37MB. `MALLOC_ARENA_MAX=2`는 차이가 없었다.
  - 768m·힙 50%도 힙이 384MB까지 커져 익명 메모리가 670MB, 스왑 21MB였다.
  - 768m·힙 40%·직접 버퍼 캐시 끔: 익명 메모리 608MB에서 멈췄고 스왑 0. `memory.peak`가 768MB에 닿는 것은 업로드 파일의 페이지 캐시이며 커널이 회수한다. 10건 동시 업로드는 0.8~2.9초였다.
  - 같은 측정에서 member·notification의 익명 메모리는 378~391MB로 M0 실측(230~335MB)보다 커졌다. M3 재측정 때 함께 본다.

서비스 15개 전망
- 컨테이너: 실측 최댓값(335MB) 기준 Spring 서비스 15개는 약 5GB, 나머지 12개는 약 4GB다. 합계 약 9GB. 상한 기준은 15 × 512MB = 7.5GB에 4GB를 더해 약 11.5GB다.
- VM 전체: 컨테이너 9GB + Docker·WSL 2GB = 약 11GB. 전체 재빌드를 함께 돌리면 4.6GB가 더해져 약 15.6GB, 컨테이너가 상한까지 차면 약 18GB다.
- 그래서 `.wslconfig`는 20GB를 권장한다. 16GB에서는 `apps`를 띄운 채 전체 재빌드를 하지 않는다([dev-setup.md](../dev-setup.md) 2절).
- 지금 서비스는 거의 빈 골격이다. Kafka 소비자, 캐시, 데이터가 붙는 M3 마감 전에 다시 재고, 부족하면 E2E를 범위별 profile 조합으로 나눠 돌린다.
