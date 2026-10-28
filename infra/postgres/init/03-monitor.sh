#!/bin/bash
# 관측성용 읽기 계정 monitor를 만든다. postgres-exporter(obs profile)가 복제 슬롯 상태와 통계를 읽는다
# (docs/architecture/observability.md). pg_monitor는 통계 뷰와 설정을 읽을 뿐 업무 테이블은 읽지 못한다.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v password="${MONITOR_DB_PASSWORD:?MONITOR_DB_PASSWORD가 필요하다}" <<'SQL'
CREATE ROLE monitor LOGIN PASSWORD :'password';
GRANT pg_monitor TO monitor;
GRANT CONNECT ON DATABASE openmarket TO monitor;
SQL
echo "관측성 계정 monitor 생성"
