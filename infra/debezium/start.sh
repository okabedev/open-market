#!/bin/bash
# connect 컨테이너의 entrypoint. Connect 워커를 띄우고 outbox 커넥터를 등록한다.
# 등록에 실패하면 워커를 내려 컨테이너가 실패로 끝나게 한다. healthcheck는 커넥터 태스크가 RUNNING인지 본다.
set -uo pipefail

/docker-entrypoint.sh start &
worker=$!
trap 'kill -TERM "$worker"; wait "$worker"' TERM INT

until curl -fs "$CONNECT_URL/connectors" > /dev/null; do
    if ! kill -0 "$worker" 2>/dev/null; then
        echo "Connect 워커가 기동 중에 끝났다" >&2
        exit 1
    fi
    sleep 2
done

if ! bash "$(dirname "$0")/register.sh"; then
    kill -TERM "$worker"
    wait "$worker"
    exit 1
fi
wait "$worker"
