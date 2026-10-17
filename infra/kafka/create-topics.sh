#!/bin/bash
# topics.txt에 선언한 토픽 중 없는 것만 만든다. kafka-init 컨테이너(apache/kafka 이미지)에서 실행한다.
# 다시 실행해도 결과가 같다. 토픽마다 JVM을 띄우므로 이미 있는 토픽은 목록 한 번으로 거른다.
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP_SERVERS:?BOOTSTRAP_SERVERS가 필요하다}"
TOPICS_FILE="$(dirname "$0")/topics.txt"
KAFKA_TOPICS=/opt/kafka/bin/kafka-topics.sh

existing="$("$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --list)"
created=0
while read -r topic partitions; do
    if grep -qxF "$topic" <<<"$existing"; then
        continue
    fi
    "$KAFKA_TOPICS" --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
        --topic "$topic" --partitions "$partitions" --replication-factor 1 > /dev/null
    echo "토픽 생성: $topic ($partitions)"
    created=$((created + 1))
done < <(grep -Ev '^\s*(#|$)' "$TOPICS_FILE")
echo "새로 만든 토픽 ${created}개"
