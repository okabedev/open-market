# 관측성

목표(NFR-10): 요청 하나를 gateway부터 Kafka 소비자까지 트레이스 하나로 따라간다. 서비스마다 RED 지표를 본다. 로그와 트레이스를 trace id로 오간다.

## 구성 (Compose `obs` profile)
```mermaid
flowchart LR
  svc[Spring 서비스] -- OTLP traces --> col[OTel Collector]
  svc -- OTLP logs --> col
  prom[Prometheus] -- scrape /actuator/prometheus --> svc
  col --> tempo[Tempo]
  col --> loki[Loki]
  grafana[Grafana] --> prom & tempo & loki
```
- 트레이스: Micrometer Tracing의 OTel 브리지로 만들고 OTLP로 Collector에 보낸다. Java agent는 쓰지 않고 Spring Boot 4의 OpenTelemetry 스타터(`spring-boot-starter-opentelemetry`)로 구성한다. `common-messaging`이 Micrometer `Tracer`·`Propagator`로 `traceparent`를 만들고, 이 조합으로 테스트했다.
- 지표: Micrometer → Prometheus 스크레이프. 서비스 목록은 Eureka 서비스 디스커버리로 찾는다.
- 로그: JSON 구조화 로그(Spring Boot 구조화 로깅). 표준 출력과 OTLP 양쪽으로 낸다. 필드에 `traceId`, `spanId`, `service`가 들어간다.
- Next.js 서버도 OTel을 붙여 BFF → gateway 구간을 같은 트레이스로 잇는다(M1).

## 설정 위치
| 파일 | 내용 |
|---|---|
| `infra/compose/obs.yaml` | 컨테이너 정의. 포트는 [dev-setup.md](../dev-setup.md) |
| `infra/observability/otel-collector.yaml` | OTLP 수신(4317, 4318) → 트레이스는 Tempo, 로그는 Loki(`/otlp`). 지표 파이프라인은 없다 |
| `infra/observability/tempo.yaml` | 단일 바이너리 모드, 로컬 디스크 저장, 보존 7일 |
| `infra/observability/loki.yaml` | 단일 바이너리 모드, 로컬 디스크 저장, 보존 7일. OTLP 로그의 `trace_id`는 구조화 메타데이터로 들어간다 |
| `infra/observability/prometheus.yml` | 스크레이프 대상. 보존 7일, exemplar 저장 켬. 관측성 스택과 discovery는 고정 대상으로, Spring 서비스는 Eureka 서비스 디스커버리(잡 `services`, 라벨 `application`)로 찾는다. IDE로 띄운 서비스는 WSL IP로 등록돼 컨테이너에서 접속된다 |
| `infra/grafana/provisioning/datasources` | Prometheus, Tempo, Loki. uid를 이름과 같게 고정한다. 지표 exemplar → 트레이스, 트레이스 → 로그, 로그 → 트레이스 링크를 건다 |

## Kafka 구간 트레이스
- 발행: outbox에 이벤트를 넣을 때 현재 트레이스 컨텍스트를 `traceparent` 컬럼에 담는다. Debezium이 헤더로 옮긴다.
- 소비: Spring Kafka 리스너 관측(`spring.kafka.listener.observation-enabled=true`)이 `traceparent` 헤더로 자식 스팬을 연다. `common-messaging`은 같은 값을 `EventEnvelope.traceparent`로도 넘긴다. 설정과 확인은 M0-d에서 한다.
- 결과: 주문 생성 요청 하나에서 `OrderPlaced` → 재고 예약 → `StockReserved` → 결제 대기까지 한 트레이스로 보인다.

## 대시보드와 알림
| 대시보드 | 내용 |
|---|---|
| 서비스 RED | 요청 수, 오류율, p50/p95/p99 지연 (서비스·엔드포인트별) |
| JVM | 힙, GC, 가상 스레드, 커넥션 풀 |
| Kafka | 소비자 지연(lag), DLT 유입 수, 무시한 이벤트 수 |
| 비즈니스 | 주문 생성 수, 결제 성공·실패, 쿠폰 발급, Saga 단계별 체류 시간 |

알림 규칙(M6): 오류율 급증, p95 목표 초과, 소비자 지연 증가, DLT 유입, outbox 미발행 행 증가. 알림은 Grafana 알림으로 로컬 Mailpit에 보낸다.

## 대시보드 관리
Grafana 데이터 소스와 대시보드는 `infra/grafana/`에 파일로 두고 프로비저닝한다. 화면에서 고친 대시보드는 JSON으로 내보내 커밋한다.
