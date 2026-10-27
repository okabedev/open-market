#!/usr/bin/env bash
# 장애 주입(R16, NFR-1·4). 레포 루트에서 `infra/fault/run.sh [장애...]`. 장애는 kafka, db, db-down, pg이고 없으면 넷 다 차례로 돈다.
#
# 1. 별도 Compose 프로젝트(openmarket-fault)를 빈 볼륨으로 띄운다. 기본·apps·e2e(가짜 PG)·fault(Toxiproxy) profile이다.
#    서비스의 DB 연결은 Toxiproxy를 거친다(SERVICE_POSTGRES_HOST). 결제 기한 등은 짧게 줄인다.
# 2. 장애마다 k6 saga.js(주문 → 결제 준비 → 가짜 PG 승인 → 결제 승인)를 돌리고, 시작 FAULT_AT초 뒤 장애를 FAULT_FOR초 넣었다가 푼다.
#    - kafka: 브로커를 멈춘다(M6 완료 기준: 1분)
#    - db: 서비스 → Postgres에 지연(FAULT_DB_LATENCY_MS, 기본 300ms ± 100ms)
#    - db-down: 서비스 → Postgres 연결을 끊는다
#    - pg: 세 번째 주문마다 가짜 PG가 승인 응답을 15초 늦게 준다(payment 읽기 타임아웃 10초보다 길다). 시간을 정해 넣지 않는다
# 3. k6가 끝나면 끝나지 않은 주문·결제가 없어질 때까지(결제 기한, 재조회 작업, 보상 취소) 기다린 뒤 check.sql과 DLT 메시지 수를 센다.
# 4. 끝나면 Compose 프로젝트를 볼륨째 지운다. 남겨서 들여다보려면 FAULT_KEEP=1.
#
# 결과: infra/fault/.out/<장애>.log(k6 출력), <장애>-check.txt(검사). 위반이나 DLT 메시지가 있으면 종료 코드 1이다.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROJECT=openmarket-fault
OUT="$ROOT/infra/fault/.out"
FAULTS=("$@")
[[ ${#FAULTS[@]} -gt 0 ]] || FAULTS=(kafka db db-down pg)
FAULT_AT=${FAULT_AT:-60}
FAULT_FOR=${FAULT_FOR:-60}
# saga.js가 상품을 만들 때 SKU마다 두는 재고. check.sql이 재고 차감을 맞춰 본다.
INITIAL_STOCK=100000

log() { printf '\n[fault] %s\n' "$*"; }
fail() { printf '\n[fault] 실패: %s\n' "$*" >&2; exit 1; }

compose() { docker compose -p "$PROJECT" --profile apps --profile e2e --profile fault "$@"; }

export SERVICE_POSTGRES_HOST=toxiproxy
export TOSS_API_URL=http://fake-pg:8090
# 결제하지 못한 주문이 검사 전에 만료되도록 결제 기한을 줄인다. saga.js는 결제 준비를 최대 3분 기다린다.
export ORDER_PAYMENT_TIMEOUT=4m
export ORDER_EXPIRY_INTERVAL=10s
export PAYMENT_CANCEL_INTERVAL=5s
export PAYMENT_REFUND_INTERVAL=5s
# k6 컨테이너 하나에서 구매자가 모두 로그인한다(infra/k6/run.sh와 같다).
export AUTH_LOGIN_RATE_LIMIT=100000
export LOAD_UID LOAD_GID
LOAD_UID=$(id -u)
LOAD_GID=$(id -g)

FAULT_PID=

cleanup() {
  local status=$?
  [[ -n "$FAULT_PID" ]] && kill "$FAULT_PID" 2>/dev/null || true
  if [[ "${FAULT_KEEP:-}" == "1" ]]; then
    log "FAULT_KEEP=1: 스택을 남긴다. 지울 때는 docker compose -p $PROJECT --profile apps --profile e2e --profile fault down -v"
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
  [[ "$f" =~ ^(kafka|db|db-down|pg)$ ]] || fail "모르는 장애: $f(kafka, db, db-down, pg)"
done
if [[ -n "$(docker ps -q --filter "publish=8080")" && -z "$(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "publish=8080")" ]]; then
  fail "다른 스택이 8080을 쓰고 있다. 개발용 스택(docker compose --profile apps stop)이나 E2E·부하 스택을 먼저 멈춘다"
fi

trap cleanup EXIT
mkdir -p "$OUT"
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
wait_for "gateway → payment" "http://localhost:8080/api/payment/v1/payments/orders/00000000-0000-0000-0000-000000000000" 401
wait_for "gateway → catalog" "http://localhost:8080/api/catalog/v1/categories" 200

toxic() { compose exec -T toxiproxy /toxiproxy-cli "$@" >/dev/null; }

# 장애를 FAULT_AT초 뒤에 넣고 FAULT_FOR초 뒤에 푼다. k6와 함께 뒤에서 돈다.
inject() {
  local fault=$1
  sleep "$FAULT_AT"
  log "장애 시작: $fault ($(date +%H:%M:%S))"
  case "$fault" in
    kafka) compose stop kafka >/dev/null ;;
    db) toxic toxic add -t latency -a "latency=${FAULT_DB_LATENCY_MS:-300}" -a jitter=100 -n fault_latency postgres ;;
    db-down) toxic toggle postgres ;;
  esac
  sleep "$FAULT_FOR"
  case "$fault" in
    kafka) compose start kafka >/dev/null ;;
    db) toxic toxic remove -n fault_latency postgres ;;
    db-down) toxic toggle postgres ;;
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
  log "장애 $fault: 주문·결제 흐름을 돌린다"
  delay_env=()
  if [[ "$fault" == "pg" ]]; then
    delay_env=(-e SAGA_PG_DELAY_MS=15000 -e SAGA_PG_DELAY_EVERY=3)
  else
    inject "$fault" &
    FAULT_PID=$!
  fi
  set +e
  docker compose -p "$PROJECT" --profile load run --rm --no-deps "${delay_env[@]}" k6 run saga.js 2>&1 | tee "$OUT/$fault.log"
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
  } | tee "$OUT/$fault-check.txt"

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
