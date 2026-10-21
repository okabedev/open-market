#!/usr/bin/env bash
# 마일스톤 완료 기준 E2E(docs/roadmap.md). 레포 루트에서 `infra/e2e/run.sh m1`.
#
# 1. 별도 Compose 프로젝트(openmarket-e2e)로 기본·apps·e2e profile을 빈 볼륨에서 띄운다. 개발용 스택(openmarket)의 데이터와 섞이지 않는다.
#    호스트 포트는 같으므로 개발용 스택과 앱(3000~3002)은 먼저 멈춘다.
#    토스 대신 가짜 PG(e2e profile, infra/fake-pg)를 쓴다. payment는 TOSS_API_URL, shop은 TOSS_SDK_URL로 주소만 바꾼다(ADR 0018).
# 2. 앱 3개를 빌드해서 `next start`로 띄운다.
# 3. clients/e2e의 Playwright 시나리오 중 `<마일스톤>-*.spec.ts`를 돌린다.
# 4. 끝나면 앱을 멈추고 Compose 프로젝트를 볼륨째 지운다. 남겨서 들여다보려면 E2E_KEEP=1.
#
# 결과: clients/e2e/playwright-report(HTML), 실패한 시나리오의 trace·스크린샷은 clients/e2e/test-results, 앱 로그는 infra/e2e/.out.
set -euo pipefail

MILESTONE=${1:?"사용법: infra/e2e/run.sh <마일스톤, 예: m1>"}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROJECT=openmarket-e2e
OUT="$ROOT/infra/e2e/.out"
APPS=(shop:3000 seller:3001 admin:3002)
APP_PIDS=()

log() { printf '\n[e2e] %s\n' "$*"; }
fail() { printf '\n[e2e] 실패: %s\n' "$*" >&2; exit 1; }

compose() { docker compose -p "$PROJECT" --profile apps --profile e2e "$@"; }

# 가짜 PG(infra/compose/e2e.yaml). payment 컨테이너는 Compose 네트워크 이름으로, 브라우저는 호스트 포트로 닿는다.
export TOSS_API_URL=http://fake-pg:8090
export TOSS_SDK_URL=http://localhost:8090/v2/standard

# 시간 설정(config-repo 기본값은 15분·1분). 결제 기한이 지나는 시나리오가 몇 십 초 안에 끝나게 줄인다(clients/e2e/support/env.ts가 같은 값을 읽는다).
export ORDER_PAYMENT_TIMEOUT=40s
export ORDER_EXPIRY_INTERVAL=2s
export PAYMENT_CANCEL_INTERVAL=2s
# 배송 시뮬레이터(config-repo 기본값은 1분 주기, 발송 10분 뒤 배송 중, 그 1시간 뒤 배송 완료). 발송 후 몇 초 안에 배송 완료가 되게 줄인다.
export FULFILLMENT_SIMULATOR_INTERVAL=2s
export FULFILLMENT_IN_TRANSIT_AFTER=3s
export FULFILLMENT_DELIVER_AFTER=3s
# 자동 구매확정(config-repo 기본값은 1시간 주기, 배송 완료 7일 뒤). "7일 뒤 자동 구매확정"을 몇 십 초로 줄인다.
# 반품할 하위 주문은 이 기한 안에 반품을 신청한다(docs/roadmap.md M4 완료 기준).
export FULFILLMENT_CONFIRM_INTERVAL=2s
export FULFILLMENT_AUTO_CONFIRM_AFTER=30s
# 가입·이메일 인증의 IP당 한도(기본 1분에 5회). 시나리오가 한 IP에서 계정을 1분 안에 여럿 만든다.
export GATEWAY_STRICT_RATE_LIMIT=60
# 로그인의 IP당 한도(기본 1분에 10회). 판매자·구매자·역할 확인 토큰으로 1분 안에 여러 번 로그인한다.
export AUTH_LOGIN_RATE_LIMIT=100

cleanup() {
  local status=$?
  # 앱마다 프로세스 그룹을 따로 만들었다. pnpm만 죽이면 그 아래 next-server가 남아 포트를 계속 잡는다.
  for pid in "${APP_PIDS[@]}"; do
    kill -- "-$pid" 2>/dev/null || true
  done
  if [[ "${E2E_KEEP:-}" == "1" ]]; then
    log "E2E_KEEP=1: 스택을 남긴다. 지울 때는 docker compose -p $PROJECT --profile apps --profile e2e down -v"
  else
    log "스택을 지운다"
    (cd "$ROOT" && compose down -v --remove-orphans >/dev/null 2>&1) || true
  fi
  exit "$status"
}

# --- 사전 확인 ---
[[ -f "$ROOT/.env" ]] || fail "레포 루트에 .env가 없다. cp .env.example .env"
ls "$ROOT/clients/e2e/tests/$MILESTONE"-*.spec.ts >/dev/null 2>&1 || fail "clients/e2e/tests/$MILESTONE-*.spec.ts가 없다"
getent hosts auth.openmarket.local >/dev/null || fail "hosts에 auth.openmarket.local이 없다(docs/dev-setup.md 3절)"
if [[ -n "$(cd "$ROOT" && docker compose ps --status running -q 2>/dev/null)" ]]; then
  fail "개발용 스택(openmarket)이 떠 있다. 포트가 겹치므로 먼저 멈춘다: docker compose --profile apps stop"
fi
for app in "${APPS[@]}"; do
  if curl -s -o /dev/null "http://localhost:${app#*:}"; then
    fail "포트 ${app#*:}에 이미 앱이 떠 있다. pnpm dev를 멈춘다"
  fi
done

trap cleanup EXIT
mkdir -p "$OUT"
cd "$ROOT"

log "Compose 프로젝트 $PROJECT를 빈 볼륨으로 띄운다(처음에는 이미지 빌드로 몇 분 걸린다)"
compose down -v --remove-orphans >/dev/null 2>&1 || true
compose up -d --wait --build

log "앱 3개를 빌드하고 띄운다"
(cd clients && corepack pnpm install --frozen-lockfile && corepack pnpm build)
for app in "${APPS[@]}"; do
  name=${app%%:*}
  setsid bash -c "cd clients && exec corepack pnpm --filter @openmarket/$name start" >"$OUT/$name.log" 2>&1 &
  APP_PIDS+=($!)
done

wait_for() {
  local what=$1 url=$2 expected=$3 seconds=${4:-120}
  for _ in $(seq "$seconds"); do
    if [[ "$(curl -s -o /dev/null -w '%{http_code}' "$url")" == "$expected" ]]; then
      return 0
    fi
    sleep 1
  done
  fail "$what이(가) 준비되지 않았다($url → $expected 기대)"
}
for app in "${APPS[@]}"; do
  wait_for "${app%%:*} 앱" "http://localhost:${app#*:}/api/health" 200
done
# member가 Eureka에 등록되고 gateway가 인스턴스를 받아야 라우팅된다. 그 전에는 503이고, 라우팅되면 토큰이 없어 401이다.
wait_for "gateway → member 라우팅" "http://localhost:8080/api/member/v1/members/me" 401

log "Playwright: clients/e2e/tests/$MILESTONE-*.spec.ts"
(cd clients && corepack pnpm --filter @openmarket/e2e exec playwright install chromium >/dev/null)
set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a
(cd clients && corepack pnpm --filter @openmarket/e2e exec playwright test "tests/$MILESTONE-")
log "통과"
