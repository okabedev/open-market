# 0025. 메시징·비즈니스 지표는 common-messaging이 내고, 복제 슬롯은 postgres-exporter로 보며, 알림은 Grafana가 Mailpit으로 보낸다
- 상태: 승인
- 날짜: 2026-10-28

## 맥락
[observability.md](../architecture/observability.md)는 M6에 대시보드 4개(서비스 RED, JVM, Kafka, 비즈니스)와 알림 5종(오류율, p95, 소비자 지연, DLT 유입, outbox 미발행)을 정했다. 장애 주입에서 복제 슬롯 무효는 저절로 복구되지 않고, 커넥터가 RUNNING인 채 발행만 멈춘다는 것을 확인했다(risks.md R16 "장애 주입"). 그래서 이것을 알리는 경보도 필요하다.

M6 전까지 서비스가 직접 만든 지표는 없었다. Boot가 내는 HTTP, JVM, Hikari, Kafka 클라이언트 지표만 있었다.
- DLT 유입, 무시한 이벤트 수, Saga 단계별 시간을 셀 곳이 없다.
- Debezium은 outbox 행에 발행 여부를 남기지 않는다. 따라서 "미발행 행"은 행 수로 셀 수 없다.
- 서비스는 다른 스키마를 읽지 않는다([ADR 0002](0002-single-postgres-schema-per-service.md)). 복제 슬롯 상태는 Postgres 카탈로그에만 있다.

## 결정
- 메시징 지표는 `common-messaging`이 낸다. 서비스 코드는 무시 지점만 고친다.
  - `messaging.outbox.events{type}`: 커밋된 outbox 이벤트 수. 비즈니스 대시보드의 주문 생성, 결제 승인·실패 수가 이 값이다. 서비스마다 업무 카운터를 따로 심지 않는다.
  - `messaging.dead.letters{topic, group}`: DLT로 보낸 메시지 수. 오류 처리기의 재시도 리스너가 센다.
  - `messaging.event.delay{listener}`: 이벤트를 outbox에 쌓은 시각부터 리스너가 처리를 마칠 때까지 걸린 시간(히스토그램). "Saga 단계별 체류 시간"은 이 값을 Saga 리스너별로 본 것이다.
  - `messaging.events.ignored{listener}`: 상태 전이가 맞지 않아 넘긴 이벤트 수. 유스케이스가 `log.warn` 대신 `IgnoredEvents.warn(log, ...)`을 부른다. 리스너 이름은 `IdempotentEventProcessor`가 핸들러를 실행하는 동안 스레드에 둔다. 같은 유스케이스를 스케줄러나 API가 부르면 로그만 남고 세지 않는다. 유스케이스 생성자를 바꾸지 않으려고 Micrometer 전역 레지스트리를 쓴다(Boot 기본값 `management.metrics.use-global-registry=true`).
- 쿠폰 발급 수는 promotion 발급 API의 HTTP 지표(2xx)로 센다. 발급은 outbox 이벤트를 내지 않는다.
- 복제 슬롯은 `prometheuscommunity/postgres-exporter`로 본다.
  - 계정은 `monitor`이고 `pg_monitor` 역할만 갖는다(`infra/postgres/init/03-monitor.sh`, 비밀번호 `MONITOR_DB_PASSWORD`).
  - outbox 미발행은 슬롯의 확인되지 않은 WAL(`current_wal_lsn - confirmed_flush_lsn`)로 잰다. Debezium이 heartbeat로 슬롯을 앞으로 보내므로, 정상이면 이 값이 작게 유지된다.
  - 슬롯 무효는 `wal_status`가 `lost`(또는 곧 잃을 `unreserved`)이거나 슬롯이 없을 때다.
- 알림은 Grafana 알림으로 만든다. 규칙, 연락처, 정책은 `infra/grafana/provisioning/alerting`에 파일로 둔다. 메일은 기본 profile의 Mailpit으로 보낸다. 대시보드도 파일로 프로비저닝한다.
- HTTP 서버 지표는 히스토그램 버킷을 낸다(5ms~10s). p95 패널과 알림이 `histogram_quantile`을 쓴다.

## 대안
- **Debezium JMX 지표(`MilliSecondsBehindSource` 등)**: connect 이미지에 JMX exporter를 넣어야 한다. 또 슬롯이 lost일 때 커넥터는 RUNNING인 채 다시 시도만 하므로, 커넥터 지표로는 무효를 구분하기 어렵다. 원인이 있는 Postgres 쪽을 직접 본다.
- **outbox 행 수를 서비스가 세기**: Debezium은 행을 고치지 않아 발행된 행과 아닌 행을 구분할 수 없다.
- **서비스마다 업무 카운터(주문 생성, 결제 승인)**: 같은 사실이 이미 outbox 이벤트로 한 번씩 기록된다. 이벤트 이름으로 세면 서비스를 고치지 않아도 된다. 업무 숫자의 원본은 여전히 업무 DB다([ADR 0023](0023-admin-ops-queries.md)).
- **무시 지점에 포트를 주입**: 유스케이스 10개와 그 테스트의 생성자를 모두 바꿔야 한다. 전역 레지스트리 하나에 기대는 대신 바꾸는 범위를 줄였다.
- **kafka-exporter로 그룹 lag**: 서비스가 죽어도 lag을 볼 수 있다. 하지만 이미지가 하나 더 늘고 Kafka 4와의 호환을 따로 확인해야 한다. 서비스가 죽은 것은 `up`과 오류율로 알 수 있으므로 소비자 스스로 내는 lag을 쓴다.
- **Prometheus 규칙 + Alertmanager**: 이미지가 하나 더 는다. observability.md가 Grafana 알림으로 정해 두었다.

## 결과
- 새 이미지 하나(postgres-exporter)와 `.env` 값 하나(`MONITOR_DB_PASSWORD`)가 는다. 이미 만든 볼륨에는 `monitor` 계정이 없으므로 직접 만들거나 볼륨을 지운다([dev-setup.md](../dev-setup.md)).
- 소비자 lag은 소비자가 살아 있어야 보인다. 서비스가 죽으면 lag 대신 `up`이 0이 된다.
- 지표는 Prometheus 보관 기간(7일)만 남고, 재시작하면 카운터가 처음부터 센다. 업무 숫자는 admin 운영 지표를 본다.
