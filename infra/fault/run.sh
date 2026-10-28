#!/usr/bin/env bash
# 장애 주입(R16, NFR-1·4). 레포 루트에서 `infra/fault/run.sh [장애...]`. 장애는 kafka, db, db-down, pg, slot, redis, coupon이고
# 없으면 모두 차례로 돈다.
#
# 1. 별도 Compose 프로젝트(openmarket-fault)를 빈 볼륨으로 띄운다. 기본·apps·e2e(가짜 PG)·fault(Toxiproxy)·obs(경보) profile이다.
#    서비스의 DB 연결은 Toxiproxy를 거친다(SERVICE_POSTGRES_HOST). 결제 기한 등은 짧게 줄인다.
# 2. 장애마다 k6 시나리오를 돌리고, 시작 FAULT_AT초 뒤 장애를 FAULT_FOR초 넣었다가 푼다.
#    saga.js(주문 → 결제 준비 → 가짜 PG 승인 → 결제 승인)를 돌리는 장애
#    - kafka: 브로커를 멈춘다(M6 완료 기준: 1분)
#    - db: 서비스 → Postgres에 지연(FAULT_DB_LATENCY_MS, 기본 300ms ± 100ms)
#    - db-down: 서비스 → Postgres 연결을 끊는다
#    - pg: 세 번째 주문마다 가짜 PG가 승인 응답을 15초 늦게 준다(payment 읽기 타임아웃 10초보다 길다). 시간을 정해 넣지 않는다
#    - slot: Debezium을 멈추고 WAL을 쌓아 복제 슬롯을 무효로 만든다(이 스택은 max_slot_wal_keep_size가 64MB). Grafana "복제 슬롯 무효"
#      경보 메일이 Mailpit에 온 뒤(최대 SLOT_ALERT_WAIT초) 운영 절차대로 슬롯을 지우고 Debezium을 다시 띄운다(R5). 복구 뒤 해결 메일도 본다
#    그 밖의 장애
#    - redis: coupon-issue.js(선착순 발급)가 도는 동안 Redis를 죽였다가(SIGKILL, 마지막 스냅숏 뒤 키를 잃는다) 다시 띄운다
#    - coupon: coupon-saga.js(쿠폰 예약 실패 조합). 장애를 넣지 않는다
# 3. k6가 끝나면 끝나지 않은 주문·결제가 없어질 때까지(결제 기한, 재조회 작업, 보상 취소) 기다린 뒤 check.sql과 DLT 메시지 수를 센다.
# 4. 끝나면 Compose 프로젝트를 볼륨째 지운다. 남겨서 들여다보려면 FAULT_KEEP=1.
#
# 결과: infra/fault/.out/<장애>.log(k6 출력), <장애>-check.txt(검사), slot-alert.txt(경보 메일). 위반이나 DLT 메시지가 있거나
# 슬롯 경보·해결 메일이 오지 않으면 종료 코드 1이다.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROJECT=openmarket-fault
OUT="$ROOT/infra/fault/.out"
FAULTS=("$@")
[[ ${#FAULTS[@]} -gt 0 ]] || FAULTS=(kafka db db-down pg slot redis coupon)
FAULT_AT=${FAULT_AT:-60}
FAULT_FOR=${FAULT_FOR:-60}
# 슬롯 무효 경보(평가 30초, 지속 30초, 묶음 대기 10초)를 기다리는 최대 시간
SLOT_ALERT_WAIT=${SLOT_ALERT_WAIT:-240}
# 시나리오가 상품을 만들 때 SKU마다 두는 재고(saga.js, coupon-saga.js). check.sql이 재고 차감을 맞춰 본다.
INITIAL_STOCK=100000

log() { printf '\n[fault] %s\n' "$*"; }
fail() { printf '\n[fault] 실패: %s\n' "$*" >&2; exit 1; }

compose() { docker compose -p "$PROJECT" --profile apps --profile e2e --profile fault --profile obs "$@"; }

export SERVICE_POSTGRES_HOST=toxiproxy
export TOSS_API_URL=http://fake-pg:8090
# 결제하지 못한 주문이 검사 전에 만료되도록 결제 기한을 줄인다. saga.js는 결제 준비를 최대 3분 기다린다.
export ORDER_PAYMENT_TIMEOUT=4m
export ORDER_EXPIRY_INTERVAL=10s
export PAYMENT_CANCEL_INTERVAL=5s
export PAYMENT_REFUND_INTERVAL=5s
# 복제 슬롯 상한(기본 1GB). slot 장애가 몇 초 만에 넘길 수 있게 줄인다. 다른 장애의 1분 트래픽은 이보다 훨씬 작다.
export POSTGRES_MAX_SLOT_WAL_KEEP_SIZE=64MB
# k6 컨테이너 하나에서 구매자가 모두 로그인한다(infra/k6/run.sh와 같다).
export AUTH_LOGIN_RATE_LIMIT=100000
# 쿠폰 발급의 사용자별 한도(기본 1분에 5회). Redis 장애 중 503을 받은 구매자가 1초마다 다시 누른다.
export GATEWAY_STRICT_RATE_LIMIT=600
export LOAD_UID LOAD_GID
LOAD_UID=$(id -u)
LOAD_GID=$(id -g)

FAULT_PID=

cleanup() {
  local status=$?
  [[ -n "$FAULT_PID" ]] && kill "$FAULT_PID" 2>/dev/null || true
  if [[ "${FAULT_KEEP:-}" == "1" ]]; then
    log "FAULT_KEEP=1: 스택을 남긴다. 지울 때는 docker compose -p $PROJECT --profile apps --profile e2e --profile fault --profile obs down -v"
  else
    log "스택을 지운다"
    (cd "$ROOT" && compose down -v --remove-orphans >/dev/null 2>&1) || true
  fi
  exit "$status"
}

# --- 사전 확인 ---
[[ -f "$ROOT/.env" ]] || fail "레포 루트에 .env가 없다. cp .env.example .env"
getent hosts auth.openmarket.local >/dev/null || fail "hosts에 auth.openmarket.local이 없다(docs/dev-setup.md 3절)"
for f in "${FAULTS[@]}"; do
  [[ "$f" =~ ^(kafka|db|db-down|pg|slot|redis|coupon)$ ]] || fail "모르는 장애: $f(kafka, db, db-down, pg, slot, redis, coupon)"
done
if [[ -n "$(docker ps -q --filter "publish=8080")" && -z "$(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "publish=8080")" ]]; then
  fail "다른 스택이 8080을 쓰고 있다. 개발용 스택(docker compose --profile apps stop)이나 E2E·부하 스택을 먼저 멈춘다"
fi

trap cleanup EXIT
# k6 결과 디렉터리를 먼저 만든다. 없으면 Docker가 root 소유로 만들어 k6(LOAD_UID)가 쓰지 못한다.
mkdir -p "$OUT" "$ROOT/infra/k6/.out"
cd "$ROOT"

log "Compose 프로젝트 $PROJECT를 빈 볼륨으로 띄운다(처음에는 이미지 빌드로 몇 분 걸린다)"
compose down -v --remove-orphans >/dev/null 2>&1 || true
# 서비스가 기동할 때 DB에 붙으므로 프록시를 먼저 띄운다.
compose up -d --wait postgres toxiproxy
compose up -d --wait --build

wait_for() {
  local what=$1 url=$2 expected=$3 seconds=${4:-180}
  for _ in $(seq "$seconds"); do
    if [[ "$(curl -s -o /dev/null -w '%{http_code}' "$url")" == "$expected" ]]; then
      return 0
    fi
    sleep 1
  done
  fail "$what이(가) 준비되지 않았다($url → $expected 기대)"
}
wait_for "gateway → member" "http://localhost:8080/api/member/v1/members/me" 401
wait_for "gateway → order" "http://localhost:8080/api/order/v1/orders" 401

# 시드 관리자의 2단계 인증을 한 번 등록하고 비밀 값을 시나리오에 넘긴다(관리자 로그인은 비밀번호 다음에 TOTP를 확인한다, ADR 0024).
enroll_admin() {
  rm -f "$ROOT/infra/k6/.out/admin-totp.json"
  docker compose -p "$PROJECT" --profile load run --rm --no-deps k6 run --quiet admin-totp.js >/dev/null
  ADMIN_TOTP_SECRET=$(grep -o '"secret":"[A-Z2-7]*"' "$ROOT/infra/k6/.out/admin-totp.json" | cut -d'"' -f4)
  [[ -n "$ADMIN_TOTP_SECRET" ]] || fail "관리자 2단계 인증을 등록하지 못했다"
  export ADMIN_TOTP_SECRET
  log "관리자 2단계 인증을 등록했다"
}
enroll_admin
wait_for "gateway → payment" "http://localhost:8080/api/payment/v1/payments/orders/00000000-0000-0000-0000-000000000000" 401
wait_for "gateway → catalog" "http://localhost:8080/api/catalog/v1/categories" 200

toxic() { compose exec -T toxiproxy /toxiproxy-cli "$@" >/dev/null; }

slot_status() { psql -At -c "SELECT slot_name || ' ' || coalesce(wal_status, '-') FROM pg_replication_slots"; }
connector_status() {
  for _ in $(seq 60); do
    curl -sf http://localhost:8083/connectors/outbox/status >/dev/null && break
    sleep 2
  done
  curl -s http://localhost:8083/connectors/outbox/status | tr -d '\n' | cut -c1-600
  echo
}

# 복제 슬롯을 무효로 만든다. Debezium을 멈춘 뒤 슬롯 상한(64MB)보다 큰 WAL을 쓰고 체크포인트로 슬롯을 끊는다.
break_slot() {
  compose stop connect >/dev/null
  psql -q -c "CREATE TABLE IF NOT EXISTS public.fault_wal_filler (x text)" \
       -c "INSERT INTO public.fault_wal_filler SELECT repeat(md5(g::text), 32) FROM generate_series(1, 200000) g" \
       -c "CHECKPOINT" >/dev/null
  log "복제 슬롯: $(slot_status)"
}

# Mailpit에서 제목에 $1이 든 메일 수. Grafana 알림 제목은 "[FIRING:1] <알림> ..." 또는 "[RESOLVED] <알림> ..."이다.
mail_count() {
  curl -s -G http://localhost:8025/api/v1/search --data-urlencode "query=subject:\"$1\"" |
    grep -o '"messages_count":[0-9]*' | cut -d: -f2
}

# 제목에 $1이 든 메일이 올 때까지 최대 $2초 기다린다. 걸린 초를 출력하고, 오지 않으면 실패를 돌려준다.
wait_mail() {
  local subject=$1 seconds=$2 started=$SECONDS
  while (( SECONDS - started < seconds )); do
    if [[ "$(mail_count "$subject")" -gt 0 ]]; then
      echo $((SECONDS - started))
      return 0
    fi
    sleep 5
  done
  return 1
}

# 운영자처럼 경보를 받은 뒤 복구한다. 결과는 slot-alert.txt에 남기고 본 루프가 판정한다.
await_slot_alert() {
  local took
  if took=$(wait_mail "FIRING:1] 복제 슬롯 무효" "$SLOT_ALERT_WAIT"); then
    log "경보: 복제 슬롯 무효 메일을 장애 ${took}초 뒤 받았다"
    echo "firing ${took}s" >"$OUT/slot-alert.txt"
  else
    log "경보: ${SLOT_ALERT_WAIT}초 안에 복제 슬롯 무효 메일이 오지 않았다"
    echo "firing missing" >"$OUT/slot-alert.txt"
  fi
}

# Debezium을 다시 띄운다.
restore_slot() {
  psql -q -c "DROP TABLE IF EXISTS public.fault_wal_filler" >/dev/null
  compose start connect >/dev/null
  log "커넥터: $(connector_status)"
  sleep 20
  log "복제 슬롯: $(slot_status)"
  # 무효(lost)가 된 슬롯은 Debezium이 스스로 복구하지 못한다. 커넥터는 RUNNING인 채 슬롯을 다시 얻으려고만 하고 발행은 멈춘다
  # (2026-10-27 확인, risks.md "장애 주입"). 운영 절차대로 슬롯을 지우면 Debezium이 새 슬롯을 만들고, 오프셋이 없어진 것을 보고
  # snapshot.mode=when_needed로 남아 있는 outbox 행을 다시 보낸다(소비자는 멱등).
  if [[ "$(slot_status)" == *" lost" ]]; then
    log "복구: 무효가 된 복제 슬롯을 지운다"
    psql -q -c "SELECT pg_drop_replication_slot('openmarket_outbox')" >/dev/null
    for _ in $(seq 60); do
      [[ "$(slot_status)" == *" reserved" ]] && break
      sleep 2
    done
    log "복제 슬롯: $(slot_status)"
  fi
}

# 장애를 FAULT_AT초 뒤에 넣고 FAULT_FOR초 뒤에 푼다. k6와 함께 뒤에서 돈다.
inject() {
  local fault=$1
  sleep "$FAULT_AT"
  log "장애 시작: $fault ($(date +%H:%M:%S))"
  case "$fault" in
    kafka) compose stop kafka >/dev/null ;;
    db) toxic toxic add -t latency -a "latency=${FAULT_DB_LATENCY_MS:-300}" -a jitter=100 -n fault_latency postgres ;;
    db-down) toxic toggle postgres ;;
    slot) break_slot ;;
    redis) compose kill redis >/dev/null ;;
  esac
  if [[ "$fault" == slot ]]; then
    await_slot_alert
  else
    sleep "$FAULT_FOR"
  fi
  case "$fault" in
    kafka) compose start kafka >/dev/null ;;
    db) toxic toxic remove -n fault_latency postgres ;;
    db-down) toxic toggle postgres ;;
    slot) restore_slot ;;
    redis) compose start redis >/dev/null ;;
  esac
  log "장애 끝: $fault ($(date +%H:%M:%S))"
}

psql() { compose exec -T postgres psql -U postgres -d openmarket -v ON_ERROR_STOP=1 "$@"; }

# 결제 기한, 재조회 작업(1분), 보상 취소가 돌아 모든 주문·결제가 끝 상태가 될 때까지 기다린다.
settle() {
  local pending
  for _ in $(seq 60); do
    pending=$(psql -At -c "SELECT (SELECT count(*) FROM \"order\".orders WHERE status NOT IN ('PAID', 'CANCELLED'))
                                + (SELECT count(*) FROM payment.payment WHERE status = 'IN_PROGRESS')")
    [[ "$pending" == "0" ]] && break
    sleep 10
  done
  # 마지막 취소의 OrderCancelled → 재고 해제·결제 보상 취소가 돌 시간
  sleep 30
}

# .DLT 토픽에 쌓인 메시지 수(끝 오프셋의 합). 재시도를 다 쓴 소비 실패가 여기로 간다.
dlt_messages() {
  compose exec -T kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server kafka:29092 --topic '.*\.DLT' |
    awk -F: '$3 > 0 { print; total += $3 } END { print "합계:" total + 0 }'
}

STATUS=0
for fault in "${FAULTS[@]}"; do
  case "$fault" in
    redis) script=coupon-issue.js ;;
    coupon) script=coupon-saga.js ;;
    *) script=saga.js ;;
  esac
  log "장애 $fault: $script를 돌린다"
  delay_env=()
  case "$fault" in
    pg) delay_env=(-e SAGA_PG_DELAY_MS=15000 -e SAGA_PG_DELAY_EVERY=3) ;;
    coupon) ;;
    *)
      inject "$fault" &
      FAULT_PID=$!
      ;;
  esac
  set +e
  docker compose -p "$PROJECT" --profile load run --rm --no-deps "${delay_env[@]}" k6 run "$script" 2>&1 | tee "$OUT/$fault.log"
  set -e
  if [[ -n "$FAULT_PID" ]]; then
    wait "$FAULT_PID" || true
    FAULT_PID=
  fi

  log "장애 $fault: 끝 상태를 기다린다"
  settle
  {
    psql -v initial_stock="$INITIAL_STOCK" -f - <"$ROOT/infra/fault/check.sql"
    echo "## DLT 메시지"
    dlt_messages
    echo "## Redis 발급 수(참고: DB 발급 수와 다를 수 있다. Redis는 앞단 필터이고 수량은 DB가 지킨다)"
    for key in $(compose exec -T redis redis-cli --scan --pattern 'promotion:coupon-stock:*'); do
      echo "$key $(compose exec -T redis redis-cli GET "$key")"
    done
  } | tee "$OUT/$fault-check.txt"

  if [[ "$fault" == slot ]]; then
    # 슬롯을 지운 뒤 Debezium이 새로 만들면 경보가 풀린다.
    if took=$(wait_mail "RESOLVED] 복제 슬롯 무효" "$SLOT_ALERT_WAIT"); then
      echo "resolved ${took}s(검사 뒤부터)" >>"$OUT/slot-alert.txt"
    else
      echo "resolved missing" >>"$OUT/slot-alert.txt"
    fi
    echo "## 경보 메일" | tee -a "$OUT/$fault-check.txt"
    tee -a "$OUT/$fault-check.txt" <"$OUT/slot-alert.txt"
    if grep -q missing "$OUT/slot-alert.txt"; then
      log "장애 slot: 복제 슬롯 경보 또는 해결 메일이 오지 않았다"
      STATUS=1
    fi
  fi

  violations=$(awk '/^## 위반/ { on = 1; next } /^## / { on = 0 } on && $NF ~ /^[0-9]+$/ && $NF > 0' "$OUT/$fault-check.txt")
  dlt=$(awk -F: '/^합계:/ { print $2 }' "$OUT/$fault-check.txt")
  if [[ -n "$violations" || "$dlt" != "0" ]]; then
    log "장애 $fault: 정합성 위반 또는 DLT 메시지가 있다. $OUT/$fault-check.txt"
    STATUS=1
  else
    log "장애 $fault: 위반 0, DLT 0"
  fi
done

log "결과: $OUT"
exit "$STATUS"
