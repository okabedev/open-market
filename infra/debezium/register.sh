#!/bin/bash
# outbox 커넥터(하나)를 등록하거나 설정을 갱신하고, 태스크가 RUNNING이 될 때까지 기다린다.
# connect 컨테이너가 기동할 때 start.sh가 실행한다. PUT이라 다시 실행해도 결과가 같다.
# 설정 설명은 docs/architecture/messaging.md의 "발행" 절에 있다. 비밀번호는 Connect 워커가 환경 변수에서 푼다(${env:...}).
set -euo pipefail

CONNECT="${CONNECT_URL:?CONNECT_URL이 필요하다}"
NAME=outbox
CONFIG="$(dirname "$0")/outbox-connector.json"

status=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H 'Content-Type: application/json' \
    --data @"$CONFIG" "$CONNECT/connectors/$NAME/config")
if [[ "$status" != 200 && "$status" != 201 ]]; then
    echo "커넥터 등록 실패: HTTP $status" >&2
    curl -s -X PUT -H 'Content-Type: application/json' --data @"$CONFIG" \
        "$CONNECT/connectors/$NAME/config" >&2 || true
    exit 1
fi
echo "커넥터 $NAME 등록(HTTP $status)"

for _ in $(seq 1 30); do
    state=$(curl -fs "$CONNECT/connectors/$NAME/status" || true)
    if grep -q '"tasks":\[{"id":0,"state":"RUNNING"' <<<"$state"; then
        echo "커넥터 $NAME RUNNING"
        exit 0
    fi
    if grep -q '"state":"FAILED"' <<<"$state"; then
        echo "커넥터 $NAME FAILED: $state" >&2
        exit 1
    fi
    sleep 2
done
echo "커넥터 $NAME이 60초 안에 RUNNING이 되지 않았다: $state" >&2
exit 1
