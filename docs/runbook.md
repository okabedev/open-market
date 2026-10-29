# 런북

장애 유형별 확인과 대응 절차다. 경보의 조건과 대시보드는 [observability.md](architecture/observability.md) "대시보드와 알림"이 원본이고, 여기에는 경보를 받은 뒤 할 일을 적는다. 명령은 레포 루트에서 개발용 스택(`docker compose`) 기준이다. 별도 프로젝트(E2E, 부하, 장애 주입)는 `-p <프로젝트>`를 붙인다.

## 공통 도구
| 무엇 | 어디 |
|---|---|
| 경보 메일 | Mailpit http://localhost:8025 (받는 주소 `ops@openmarket.local`). 풀리면 `[RESOLVED]` 메일이 온다 |
| 대시보드 | Grafana http://localhost:3003 폴더 "Open Market": 서비스 RED, JVM, Kafka와 outbox, 비즈니스 |
| 요청 추적 | 응답 헤더 `X-Request-Id`(= trace id)로 Grafana Explore의 Tempo(트레이스)와 Loki(`{service_name="<서비스>"} \| trace_id="<trace id>"`) |
| 보안 이벤트 | Loki `\|= "보안 이벤트"`([observability.md](architecture/observability.md) "보안 이벤트") |
| DLT 메시지 | admin "DLT 메시지" 화면(서비스 전체를 합쳐 보인다) |
| 결제 실패 | admin "결제 실패" 화면(환불 실패, 결제 취소 거절) |
| 감사 로그 | admin "감사 로그" 화면 |
| 커넥터 | `curl -s localhost:8083/connectors/outbox/status`, `docker compose logs connect` |
| Kafka | Kafka UI http://localhost:8089, 또는 `docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups` |
| DB | `docker compose exec postgres psql -U postgres -d openmarket` |
| 서비스 로그·재시작 | `docker compose --profile apps logs --since 15m <서비스>`, `docker compose --profile apps restart <서비스>` |

조치를 바꾸는 작업(DLT 다시 보내기·버리기, 결제 재시도, TOTP 등록 해제)은 감사 로그에 남는다. 화면이 없는 DB 작업은 이 문서의 스크립트만 쓴다.

## 경보별 대응

### 오류율 급증
1. RED 대시보드의 엔드포인트 표에서 5xx가 난 엔드포인트를 찾는다.
2. 그 서비스의 Loki에서 `|= "ERROR"`로 예외를 보고, trace id로 Tempo에서 어느 구간(DB, 다른 서비스 호출, Redis)에서 실패했는지 본다.
3. 원인별 조치
   - DB 연결 실패·풀 고갈: JVM 대시보드 커넥션 풀 대기. Postgres가 살아 있는지(`docker compose ps postgres`), 연결 수가 `max_connections`(100)에 닿았는지(`SELECT count(*) FROM pg_stat_activity`) 본다. 원인을 없앤 뒤 서비스를 재시작한다.
   - 다른 서비스 호출 실패(OpenFeign 조회): 호출받는 서비스를 같은 순서로 본다.
   - Redis: 아래 "Redis 장애".
   - 토스 응답 실패: payment 로그. 토스 장애면 결제 승인은 실패 이벤트로 끝나고 주문은 결제 기한이 지나면 취소된다. 조치할 것은 없다.
4. 풀리면 해결 메일이 온다.

### p95 지연 목표 초과
1. RED 대시보드 엔드포인트별 p95에서 느린 엔드포인트를 찾는다. 서비스 전체가 느리면 JVM 대시보드(GC 멈춤, 힙 상한, 커넥션 대기)를 본다.
2. Tempo에서 느린 트레이스를 열어 가장 긴 구간을 본다. DB 쿼리면 `pg_stat_activity`의 오래 도는 쿼리와 잠금 대기(`wait_event_type = 'Lock'`)를 본다.
3. 힙이 상한에 붙어 있고 GC가 잦으면 서비스를 재시작해 시간을 벌고, 힙 덤프나 부하 원인을 따로 찾는다([risks.md](architecture/risks.md) "메모리 예산").
4. gateway만 느리면 레이트 리밋 Redis 호출이 원인일 수 있다([risks.md](architecture/risks.md) "부하 측정"의 남은 병목).

### 소비자 지연 증가
1. Kafka와 outbox 대시보드에서 lag이 쌓이는 소비자(서비스·토픽)와 이벤트 처리 지연 p95를 본다.
2. 처리 지연이 길면 그 서비스의 DB·다른 서비스 호출이 느린 것이다. "p95 지연 목표 초과"와 같은 순서로 본다.
3. 처리가 아예 없으면 서비스가 멈췄거나 리스너가 그룹에서 빠진 것이다. `kafka-consumer-groups.sh --describe --group <그룹>`에 소비자가 없으면 서비스 로그를 보고 재시작한다. 소비자는 멱등이라 재시작으로 다시 읽어도 결과가 같다.
4. 한 메시지에서 계속 실패하면 재시도 뒤 DLT로 넘어가고 lag은 풀린다. "DLT 유입"으로 이어서 본다.

### DLT 유입
1. 메일의 서비스·토픽·그룹으로 admin "DLT 메시지" 화면에서 `PENDING` 행을 연다. 예외 클래스와 trace id로 Loki에서 실패 로그를 본다.
2. 원인을 먼저 고친다. 같은 원인으로 다시 실패하면 새 행이 하나 더 생길 뿐이다.
   - 일시 장애(DB·Redis·다른 서비스)였으면 그대로 다시 보낸다.
   - 코드 결함이면 고쳐서 배포한 뒤 다시 보낸다.
   - 처리할 수 없는 메시지(형식이 틀린 옛 이벤트 등. 예: [fulfillment.md](architecture/fulfillment.md)의 M4 이전 `OrderPaid`)는 사유를 적고 버린다.
3. 다시 보낸 뒤 확인한다. 다시 보낸 메시지는 같은 키의 뒤 이벤트보다 늦게 오므로, 그 사이 집계 상태가 바뀌었으면 소비자가 무시한다([messaging.md](architecture/messaging.md) "소비: 멱등 소비자"의 순서). Kafka와 outbox 대시보드의 "무시한 이벤트"가 그 리스너에서 늘었으면 이벤트의 효과가 적용되지 않은 것이다.
   - 이때는 집계를 직접 본다. 예: 주문 취소 이벤트가 무시돼 재고 예약이 남았으면 `infra/fault/check.sql`의 짝 검사로 남은 예약을 찾는다.
   - 업무 화면의 조치(결제 재시도, 클레임 중재 등)로 맞춘다. DB를 직접 고치지 않는다.
4. 같은 이벤트 ID의 행은 화면에 함께 보인다. 하나를 처리하면 나머지는 버린다.

### outbox 미발행 증가
복제 슬롯의 미확인 WAL이 16MiB를 넘었다. Debezium이 outbox를 읽지 못하고 있다.
1. 커넥터 상태를 본다: `curl -s localhost:8083/connectors/outbox/status`.
2. 상태별 조치
   - 커넥터나 태스크가 `FAILED`: `trace`를 읽는다. 선언하지 않은 토픽이면 `infra/kafka/topics.txt`에 선언하고 `docker compose up -d kafka-init`으로 만든 뒤 `curl -X POST localhost:8083/connectors/outbox/tasks/0/restart`로 재시작한다([messaging.md](architecture/messaging.md) "발행"의 장애 때 동작). 유실은 없다.
   - connect 컨테이너가 멈춤: `docker compose up -d --wait connect`. 기동할 때 커넥터를 다시 등록한다.
   - `RUNNING`인데 WAL이 계속 쌓임: Kafka가 살아 있는지 본다. Kafka가 잠깐 끊겼다 돌아오면 워커가 바로 다시 받는다.
3. 슬롯이 `unreserved`나 `lost`가 되면 "복제 슬롯 무효" 경보도 온다. 그 절차를 따른다.

### 복제 슬롯 무효
슬롯 `openmarket_outbox`가 `max_slot_wal_keep_size`(개발 1GB)를 넘어 WAL을 잃었다. **저절로 복구되지 않는다.** 커넥터는 `RUNNING`으로 보이지만 발행이 멈춰 있고, 그동안 쓴 이벤트(결제 준비, 취소 등)가 나가지 않아 주문이 결제 기한을 넘겨 만료된다([risks.md](architecture/risks.md) "장애 주입").

1. 슬롯 상태를 본다.
   ```
   docker compose exec postgres psql -U postgres -d openmarket -c "SELECT slot_name, active, wal_status FROM pg_replication_slots"
   ```
   - `unreserved`: 아직 잃지 않았다. 커넥터가 따라잡게 둔다. 커넥터가 멈춰 있으면 "outbox 미발행 증가" 2단계로 바로 띄운다.
   - `lost`: 다음 단계로 간다.
2. 무효가 된 슬롯을 지운다. `active`가 `t`라 지워지지 않으면 `docker compose stop connect` 뒤 지우고 `docker compose up -d --wait connect`로 다시 띄운다.
   ```
   docker compose exec postgres psql -U postgres -d openmarket -c "SELECT pg_drop_replication_slot('openmarket_outbox')"
   ```
3. Debezium이 새 슬롯을 만들고, 오프셋이 쓸 수 없게 된 것을 보고 스냅샷(`snapshot.mode=when_needed`)으로 남아 있는 outbox 행을 모두 다시 보낸다. 이미 보낸 이벤트도 다시 가지만 소비자가 이벤트 ID로 거른다. 1단계 명령으로 `wal_status`가 `reserved`가 되는지 본다(1분 안팎).
4. 확인
   - 해결 메일이 온다.
   - Kafka와 outbox 대시보드의 outbox 이벤트·처리한 이벤트가 다시 늘고, 소비자 lag이 줄어든다.
   - 결제 대기에 머문 주문이 결제 기한 안에 결제 완료나 취소로 간다.
5. **7일 안에 복구한다.** outbox 정리 작업은 발행 여부와 상관없이 7일이 지난 행을 지운다. 그 뒤에 슬롯을 지우면 스냅샷이 그 이벤트를 다시 보낼 수 없다.

이 절차는 `infra/fault/run.sh slot`이 경보를 받은 뒤 그대로 실행해 확인한다(주문 361건 결제 완료, 위반 0, DLT 0, [risks.md](architecture/risks.md) "장애 주입").

### 복제 슬롯 없음
슬롯이 없거나 postgres-exporter가 응답하지 않는다.
1. `up{job="postgres"}`(Grafana Explore, Prometheus)가 0이면 exporter 문제다. `docker compose --profile obs logs postgres-exporter`에서 `monitor` 계정 로그인 실패가 보이면 [dev-setup.md](dev-setup.md) 4절대로 계정을 만든다. 발행에는 영향이 없다.
2. exporter가 정상이면 슬롯이 정말 없다. 커넥터가 기동하며 슬롯을 만드므로 connect 상태를 본다. connect가 healthy가 아니면 "outbox 미발행 증가" 2단계를 따른다.
3. 새 스택은 슬롯이 생기기 전 1~2분 동안 슬롯이 없다. 3분 지속 조건이라 보통은 경보가 오지 않는다.

### 보안 이벤트 알림
로그인 실패 급증, 로그인 레이트 리밋 초과, 계정 잠금 다수, 관리자 2단계 코드 실패, 접근 거부·토큰 거절 급증. 조건은 [observability.md](architecture/observability.md) "대시보드와 알림"이다.
1. Grafana Explore(Loki)에서 메일의 이벤트를 모아 본다. 예: `{service_name="auth"} |= "보안 이벤트 LOGIN_FAILED"`. 줄마다 계정 ID(없는 계정은 `unknown`)와 IP가 있다. gateway 이벤트는 주체(`sub`), 메서드, 경로다.
2. IP별로 센다: `sum by (ip) (count_over_time({service_name="auth"} |= "보안 이벤트" | regexp "ip=(?P<ip>\\S+)" [10m]))`.
   - 한 IP에 몰려 있으면 비밀번호 추측이다. 개발 환경에는 차단 장치가 없다. 운영 배치에서는 앞단(리버스 프록시)에서 그 IP를 막는다.
   - 여러 IP에 고르게 퍼져 있고 계정이 대부분 `unknown`이면 유출된 목록으로 대입하는 것이다. 성공한 로그인이 섞였는지 같은 IP의 auth 요청 로그를 본다.
3. 관리자 2단계 코드 실패는 관리자 비밀번호가 맞았다는 뜻이다. 그 계정의 관리자에게 본인이 맞는지 바로 확인한다. 본인이 아니면 비밀번호를 바꾸게 하고, 기기를 잃었으면 "관리자 TOTP 등록 해제"를 따른다. admin "감사 로그"에서 그 계정의 최근 작업을 본다.
4. 접근 거부가 한 주체(`sub`)에서 나오면 그 계정이 권한 밖 API를 훑는 것이다. 판매자·구매자 계정이면 관리자 화면에서 정지를 검토한다. 토큰 거절은 위조·만료 토큰이라 주체가 없다. 경로를 보고 IP를 막는다.
5. 잠금은 15분 뒤 저절로 풀린다. 아래 "계정 잠금과 로그인 공격"을 함께 본다.

## 경보 밖의 운영 작업

### 결제 취소·환불 실패
토스가 거절한 환불과 보상 취소는 자동으로 다시 부르지 않는다. 환불 실패는 관리자 인앱 알림으로도 온다.
1. admin "결제 실패" 화면에서 실패 코드를 본다.
2. 토스 상점관리자에서 그 결제의 상태를 확인한다. 이미 취소됐거나 잔액이 없으면 원인을 기록하고 다시 시도하지 않는다.
3. 원인이 풀렸으면(토스 일시 장애, 잔액 정정) 화면에서 다시 시도한다. 시도 번호가 올라 새 멱등성 키로 부른다([payment.md](architecture/payment.md) "관리자 재시도").

### Redis 장애
Redis에 닿지 못하면 레이트 리밋(gateway, auth 로그인)은 요청을 통과시키고, 선착순 쿠폰 발급은 503 `PROMOTION_UNAVAILABLE`이다. 주문·결제는 영향이 없다([data.md](architecture/data.md) "캐시와 기타 저장소").
1. `docker compose ps redis`, `docker compose logs redis`로 상태를 보고 `docker compose up -d --wait redis`로 띄운다.
2. 다시 연결되면 서비스는 저절로 이어서 쓴다. 쿠폰 발급 수는 promotion DB가 원본이라 초과 발급은 없다([risks.md](architecture/risks.md) "장애 주입").
3. 장애 동안 레이트 리밋이 풀려 있었으므로 보안 이벤트와 가입 수를 확인한다.

### 관리자 TOTP 등록 해제
관리자가 인증 앱 기기를 잃었거나 복구 코드를 다 썼을 때 등록을 지운다. 다음 로그인에 비밀번호 다음 등록 화면이 나온다([auth.md](architecture/auth.md) "관리자 2단계 인증", [ADR 0024](adr/0024-admin-totp-mfa.md)). 마지막 관리자도 쓸 수 있게 화면이 아니라 DB 절차다.
1. 본인 확인을 등록 경로 밖에서 한다(얼굴, 등록된 연락처로 다시 걸기). 계정을 빼앗긴 것이 의심되면 비밀번호부터 바꾸게 한다.
2. 실행한다. `actor`는 처리한 관리자 계정 ID, `reason`은 사유이고 감사 로그에 남는다. 개인정보를 적지 않는다.
   ```
   docker compose exec -T postgres psql -U postgres -d openmarket \
     -v email=admin@openmarket.local -v actor=<관리자 계정 ID> -v reason='기기 분실' < infra/runbook/totp-reset.sql
   ```
   - 한 트랜잭션에서 등록과 복구 코드, 그 계정에 발급한 인가 코드·토큰을 지우고 감사 로그(`TOTP_ENROLLMENT_RESET`)를 쓴다.
   - 다시 실행해도 결과가 같고 감사 행도 하나다. 없는 이메일이면 아무것도 바꾸지 않고 실패한다.
3. admin "감사 로그" 화면에서 auth의 "2단계 인증 등록 해제" 행을 확인한다.
4. 관리자가 다시 로그인해 등록하고 새 복구 코드를 보관한다. 리프레시 토큰이 지워졌으므로 열려 있던 admin 세션은 액세스 토큰이 만료되면 로그인 화면으로 간다.

이 SQL은 auth 통합 테스트(`AdminSecondFactorIntegrationTest`)가 같은 방법(psql)으로 실행해 검증한다.

### 계정 잠금과 로그인 공격
- 로그인 5회 실패나 2단계 코드 5회 실패는 계정을 15분 잠근다(ACC-1). 잠금은 시간이 지나면 풀린다. 관리자가 미리 풀어 주는 절차는 두지 않는다.
- Loki에서 `|= "보안 이벤트"`로 모아 본다. 한 IP의 `LOGIN_RATE_LIMITED`가 이어지면 비밀번호 추측이다. 여러 계정의 `ACCOUNT_LOCKED`가 한꺼번에 나오면 남의 계정을 잠그는 공격이다([security.md](architecture/security.md) "받아들인 위험").
- 개발 환경에는 IP 차단 장치가 없다. 운영 배치에서는 앞단(리버스 프록시)에서 그 IP를 막는다.
