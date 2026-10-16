#!/bin/bash
# Debezium 복제 계정과 publication을 만든다. 형식은 docs/architecture/data.md를 따른다.
#
# outbox_event 테이블은 각 서비스의 Flyway가 나중에 만들기 때문에 여기서는 빈 publication만 만든다.
# 테이블 SELECT 권한과 publication 등록은 커넥터 등록 스크립트(infra/debezium, M0-d)가 한다.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v password="${DEBEZIUM_DB_PASSWORD:?DEBEZIUM_DB_PASSWORD가 필요하다}" <<'SQL'
CREATE ROLE debezium LOGIN REPLICATION PASSWORD :'password';
GRANT CONNECT ON DATABASE openmarket TO debezium;
CREATE PUBLICATION openmarket_outbox;
SQL

# 스키마 안을 들여다볼 수 있게만 한다. 테이블 SELECT는 outbox_event에만 따로 준다.
mapfile -t SERVICES < <(grep -Ev '^\s*(#|$)' /docker-entrypoint-initdb.d/services.txt)
for schema in "${SERVICES[@]}"; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -v schema="$schema" <<'SQL'
GRANT USAGE ON SCHEMA :"schema" TO debezium;
SQL
done
echo "복제 계정 debezium, publication openmarket_outbox 생성"
