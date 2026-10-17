# 기술 리스크

M0에서 먼저 확인할 항목이다. 확인하면 결과와 날짜를 적고, 결정이 생기면 ADR로 남긴다.

| # | 리스크 | 왜 문제인가 | 확인 방법 | 마일스톤 | 상태 |
|---|---|---|---|---|---|
| R1 | Java 25 + Spring Boot 4 + Spring Cloud 호환 조합 | Spring Cloud 릴리스 트레인이 쓰려는 Boot 버전을 지원해야 한다 | 공식 호환표와 start.spring.io 메타데이터로 확인하고, 빈 서비스를 빌드·기동한다 | M0-b | 확인(2026-10-16): Boot 4.1.1 + Cloud 2025.1.3, Java 25 지원. [ADR 0009](../adr/0009-version-matrix.md) |
| R2 | QueryDSL APT와 Java 25, Hibernate 7 | 원본 QueryDSL은 유지보수가 느리다. 포크(OpenFeign) 버전의 Jakarta·JDK 지원을 확인해야 한다 | 템플릿 서비스에서 Q-타입 생성과 조회 테스트 | M0-b | 확인(2026-10-16): QueryDSL 7.7 + `annotationProcessor` 경로로 Java 25에서 Q-타입 생성. `ConventionPluginsTest` |
| R3 | 포매터·정적 분석 도구의 Java 25 지원 | 도구가 새 문법을 못 읽으면 빌드가 깨진다 | 후보 도구로 템플릿 빌드 | M0-b | 확인(2026-10-16): palantir 제외, google-java-format 1.36.1 + Error Prone + NullAway 동작. [ADR 0010](../adr/0010-code-style-tools.md) |
| R4 | Gateway 구현 선택(WebFlux 또는 WebMVC) | 레이트 리밋, 토큰 처리 필터 지원 범위가 다르다 | 두 구현의 필터 문서 비교, 필요한 필터(JWT, Redis 레이트 리밋, 경로 재작성) 동작 확인 | M0-c | 확인(2026-10-16): WebFlux 선택. Redis 레이트 리밋을 설정으로 쓸 수 있다(WebMVC는 Bucket4j, 키 리졸버는 Java DSL만). JWT·역할·레이트 리밋·접두어 제거를 `GatewayTest`, `RateLimitTest`로 확인. Redis 무응답 시 기본 타임아웃(60s)만큼 요청이 멈추는 것을 발견해 200ms로 줄였다(`RedisOutageTest`). [ADR 0011](../adr/0011-gateway-webflux.md) |
| R5 | Debezium과 Postgres 논리 복제 | 스키마 여러 개의 outbox를 커넥터 하나로 읽고, 토픽을 `aggregate_type`으로 나눠야 한다. 복제 슬롯이 쌓이면 디스크가 찬다 | 샘플 서비스 두 개로 outbox → 토픽 확인, 커넥터 중지 후 재기동 시 유실 없는지 확인 | M0-d | 미확인 |
| R6 | 로컬 메모리 예산 | 서비스 15개, Kafka, Connect, ES, 관측성을 함께 띄워야 한다. WSL 메모리가 부족하면 E2E를 돌릴 수 없다 | profile별로 띄워 `docker stats`로 실측하고, 서비스 JVM 힙 기본값(`-XX:MaxRAMPercentage`)을 정한다 | M0-c | 확인(2026-10-17): 전체 profile 실측 5.1GB(Spring 서비스 4개). 상한이 없으면 JVM이 VM 메모리 기준으로 힙 상한을 5GB로 잡는 것을 발견했다. JVM 컨테이너에 `mem_limit` 512m + `MaxRAMPercentage=50`을 두었다. 서비스 15개 기준 컨테이너 상한 합계 약 11.5GB, 빌드까지 VM 전체 약 18GB로 `.wslconfig` 20GB를 권장한다. 아래 "메모리 예산". 서비스에 Kafka 소비자와 데이터가 붙는 M3 마감 전에 다시 잰다 |
| R7 | auth issuer 호스트 이름 | 브라우저, IDE 실행 서비스, 컨테이너가 같은 issuer URL로 auth에 닿아야 한다 | hosts + 네트워크 별칭으로 세 경우 모두 토큰 검증 확인 | M1 | 미확인 |
| R8 | Next.js BFF 세션과 토큰 갱신 | 서버 컴포넌트, Server Action, Route Handler 모두에서 세션을 읽고 토큰을 갱신해야 한다. 동시 갱신 경쟁이 생길 수 있다 | 갱신 중복 방지(Redis 락) 시나리오 테스트 | M1 | 미확인 |
| R9 | 토스페이먼츠 테스트 환경 | 테스트 키로 승인, 부분취소, 웹훅을 로컬에서 받을 수 있어야 한다. 웹훅은 공인 주소가 필요하다 | 승인·취소 API 확인. 웹훅은 결제 조회 폴링으로 대신할 수 있는지 확인 | M3 | 미확인 |
| R10 | Elasticsearch nori 플러그인 | 공식 이미지에 nori가 없어서 커스텀 이미지가 필요하다 | Dockerfile로 플러그인 설치, 인덱스 분석기 테스트 | M2 | 미확인 |

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

서비스 15개 전망
- 컨테이너: 실측 최댓값(335MB) 기준 Spring 서비스 15개는 약 5GB, 나머지 12개는 약 4GB다. 합계 약 9GB. 상한 기준은 15 × 512MB = 7.5GB에 4GB를 더해 약 11.5GB다.
- VM 전체: 컨테이너 9GB + Docker·WSL 2GB = 약 11GB. 전체 재빌드를 함께 돌리면 4.6GB가 더해져 약 15.6GB, 컨테이너가 상한까지 차면 약 18GB다.
- 그래서 `.wslconfig`는 20GB를 권장한다. 16GB에서는 `apps`를 띄운 채 전체 재빌드를 하지 않는다([dev-setup.md](../dev-setup.md) 2절).
- 지금 서비스는 거의 빈 골격이다. Kafka 소비자, 캐시, 데이터가 붙는 M3 마감 전에 다시 재고, 부족하면 E2E를 범위별 profile 조합으로 나눠 돌린다.
