#!/usr/bin/env bash
# 부하 시험(NFR-9). 레포 루트에서 `infra/k6/run.sh [시나리오...]`. 시나리오는 browse, order, coupon이고 없으면 셋 다 돈다.
#
# 1. 별도 Compose 프로젝트(openmarket-load)로 기본·apps·search·obs profile을 빈 볼륨에서 띄운다. 개발용 스택과 섞이지 않는다.
#    호스트 포트는 같으므로 개발용 스택과 E2E 스택은 먼저 멈춘다.
# 2. 시나리오마다 k6 컨테이너(load profile)를 돌리고, 도는 동안 컨테이너 CPU·메모리를 5초마다 기록한다.
# 3. 시나리오가 끝나면 컨테이너 cgroup 메모리(anon, peak, 상한 도달)와 Prometheus의 연결 풀 대기를 남긴다(R6, R13).
# 4. 끝나면 Compose 프로젝트를 볼륨째 지운다. 남겨서 들여다보려면 LOAD_KEEP=1.
#
# 결과: infra/k6/.out/<이름>.log(k6 출력), <이름>-summary.json, <이름>-stats.csv, <이름>-memory.txt, <이름>-pool.txt.
#       이름은 시나리오 이름이고, LOAD_LABEL을 주면 <시나리오>-<라벨>이다(같은 시나리오를 조건을 바꿔 여러 번 돌릴 때).
# 스택을 남겨 두고 다시 돌리려면 LOAD_KEEP=1로 띄운 뒤 LOAD_REUSE=1(빈 볼륨으로 다시 만들지 않는다).
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROJECT=openmarket-load
OUT="$ROOT/infra/k6/.out"
SCENARIOS=("$@")
[[ ${#SCENARIOS[@]} -gt 0 ]] || SCENARIOS=(browse order coupon)

log() { printf '\n[load] %s\n' "$*"; }
fail() { printf '\n[load] 실패: %s\n' "$*" >&2; exit 1; }

compose() { docker compose -p "$PROJECT" --profile apps --profile search --profile obs "$@"; }

# 로그인 한도(기본 IP마다 1분에 10회). auth는 실제 접속 주소를 보므로 k6 컨테이너 하나에서 구매자 1,000명이 로그인하려면 늘려야 한다.
# gateway 한도는 그대로 둔다. k6가 사용자마다 다른 X-Forwarded-For를 넣는다(infra/k6/lib/env.js).
export AUTH_LOGIN_RATE_LIMIT=100000
export LOAD_UID LOAD_GID
LOAD_UID=$(id -u)
LOAD_GID=$(id -g)

SAMPLER_PID=

cleanup() {
  local status=$?
  [[ -n "$SAMPLER_PID" ]] && kill "$SAMPLER_PID" 2>/dev/null || true
  if [[ "${LOAD_KEEP:-}" == "1" ]]; then
    log "LOAD_KEEP=1: 스택을 남긴다. 지울 때는 docker compose -p $PROJECT --profile apps --profile search --profile obs down -v"
  else
    log "스택을 지운다"
    (cd "$ROOT" && compose down -v --remove-orphans >/dev/null 2>&1) || true
  fi
  exit "$status"
}

# --- 사전 확인 ---
[[ -f "$ROOT/.env" ]] || fail "레포 루트에 .env가 없다. cp .env.example .env"
getent hosts auth.openmarket.local >/dev/null || fail "hosts에 auth.openmarket.local이 없다(docs/dev-setup.md 3절)"
for s in "${SCENARIOS[@]}"; do
  [[ -f "$ROOT/infra/k6/$s.js" ]] || fail "infra/k6/$s.js가 없다"
done
if [[ -n "$(docker ps -q --filter "publish=8080")" && -z "$(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "publish=8080")" ]]; then
  fail "다른 스택이 8080을 쓰고 있다. 개발용 스택(docker compose --profile apps stop)이나 E2E 스택을 먼저 멈춘다"
fi

trap cleanup EXIT
mkdir -p "$OUT"
cd "$ROOT"

if [[ "${LOAD_REUSE:-}" != "1" ]]; then
  log "Compose 프로젝트 $PROJECT를 빈 볼륨으로 띄운다(처음에는 이미지 빌드로 몇 분 걸린다)"
  compose down -v --remove-orphans >/dev/null 2>&1 || true
fi
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
# 서비스가 Eureka에 등록되고 gateway가 인스턴스를 받아야 라우팅된다. 그 전에는 503이다.
wait_for "gateway → member" "http://localhost:8080/api/member/v1/members/me" 401
wait_for "gateway → order" "http://localhost:8080/api/order/v1/orders" 401
wait_for "gateway → promotion" "http://localhost:8080/api/promotion/v1/issued-coupons" 401
wait_for "gateway → search" "http://localhost:8080/api/search/v1/products?q=x" 200
wait_for "gateway → catalog" "http://localhost:8080/api/catalog/v1/categories" 200

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

# 컨테이너 CPU·메모리를 5초마다 남긴다. k6 자신도 들어간다(R13: 부하 발생기가 CPU를 나눠 쓴다).
sample() {
  echo "time,name,cpu,mem"
  while true; do
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' |
      grep -E "^$PROJECT-" | sed "s/^/$(date +%H:%M:%S),/"
    sleep 5
  done
}

# 컨테이너마다 cgroup v2 메모리. anon은 익명 메모리, peak는 컨테이너가 뜬 뒤 최대, max는 상한에 닿은 횟수,
# oom_kill은 상한 때문에 죽은 프로세스 수다(risks.md R6과 같은 값). restarts는 컨테이너가 다시 시작된 횟수다.
memory() {
  printf '%-40s %10s %10s %6s %8s %8s\n' container anon_MB peak_MB max oom_kill restarts
  for c in $(docker ps --format '{{.Names}}' --filter "label=com.docker.compose.project=$PROJECT" | sort); do
    restarts=$(docker inspect -f '{{.RestartCount}}' "$c")
    docker exec "$c" sh -c '
      anon=$(grep "^anon " /sys/fs/cgroup/memory.stat | cut -d" " -f2)
      peak=$(cat /sys/fs/cgroup/memory.peak 2>/dev/null || echo 0)
      max=$(grep "^max " /sys/fs/cgroup/memory.events | cut -d" " -f2)
      oom=$(grep "^oom_kill " /sys/fs/cgroup/memory.events | cut -d" " -f2)
      echo "$anon $peak $max $oom"' 2>/dev/null |
      awk -v c="$c" -v r="$restarts" '{ printf "%-40s %10d %10d %6d %8d %8d\n", c, $1/1048576, $2/1048576, $3, $4, r }' || true
  done
}

# 서비스별 연결 풀(서비스당 5개, data.md "연결 수") 대기와 사용 중 최대값. 대기가 0보다 크면 풀을 기다린 요청이 있었다.
pool() {
  local window=$1
  for q in "max by (application) (max_over_time(hikaricp_connections_pending[$window]))" \
           "max by (application) (max_over_time(hikaricp_connections_active[$window]))"; do
    echo "# $q"
    curl -s --get http://localhost:9090/api/v1/query --data-urlencode "query=$q" |
      python3 -c '
import json, sys
rows = json.load(sys.stdin)["data"]["result"]
for r in sorted(rows, key=lambda r: r["metric"].get("application", "")):
    print(r["metric"].get("application", "?").ljust(15), r["value"][1])'
  done
}

STATUS=0
for s in "${SCENARIOS[@]}"; do
  name="$s${LOAD_LABEL:+-$LOAD_LABEL}"
  log "시나리오 $name"
  sample >"$OUT/$name-stats.csv" &
  SAMPLER_PID=$!
  started=$(date +%s)
  set +e
  docker compose -p "$PROJECT" --profile load run --rm --no-deps k6 run \
    --summary-export "/out/$name-summary.json" "$s.js" 2>&1 | tee "$OUT/$name.log"
  code=${PIPESTATUS[0]}
  set -e
  kill "$SAMPLER_PID" 2>/dev/null || true
  SAMPLER_PID=
  memory >"$OUT/$name-memory.txt"
  pool "$(( $(date +%s) - started + 30 ))s" >"$OUT/$name-pool.txt" || true
  if [[ $code -ne 0 ]]; then
    log "$name: k6 종료 코드 $code(임계값을 넘었거나 실패). 결과는 $OUT/$name.log"
    STATUS=1
  fi
done

log "결과: $OUT"
exit "$STATUS"
