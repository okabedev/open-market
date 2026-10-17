#!/bin/bash
# Debezium 복제 계정, publication, outbox 자동 등록 트리거, heartbeat 테이블을 만든다. 형식은 docs/architecture/data.md를 따른다.
#
# outbox_event 테이블은 각 서비스의 Flyway가 나중에 만든다. 그래서 테이블이 생기는 순간 event trigger가
# publication 등록과 SELECT 권한 부여를 한다. 서비스를 추가해도 여기를 고칠 필요가 없다.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v password="${DEBEZIUM_DB_PASSWORD:?DEBEZIUM_DB_PASSWORD가 필요하다}" <<'SQL'
CREATE ROLE debezium LOGIN REPLICATION PASSWORD :'password';
GRANT CONNECT ON DATABASE openmarket TO debezium;

-- outbox는 INSERT만 보낸다. 정리 작업의 DELETE는 복제하지 않는다. UPDATE는 heartbeat 테이블 때문에 둔다.
CREATE PUBLICATION openmarket_outbox WITH (publish = 'insert, update');

-- outbox 테이블만 변경되는 일이 드물어도 Debezium이 LSN을 확인할 수 있게, 커넥터가 주기적으로 이 행을 고친다
-- (heartbeat.action.query, docs/architecture/risks.md R5). 이 테이블의 변경 이벤트는 커넥터 SMT가 버린다.
CREATE SCHEMA debezium AUTHORIZATION debezium;
CREATE TABLE debezium.heartbeat (
    id      int         PRIMARY KEY,
    beat_at timestamptz NOT NULL
);
ALTER TABLE debezium.heartbeat OWNER TO debezium;
INSERT INTO debezium.heartbeat VALUES (1, now());
ALTER PUBLICATION openmarket_outbox ADD TABLE debezium.heartbeat;

-- 어느 스키마든 outbox_event 테이블이 생기면 publication에 넣고 debezium에 SELECT 권한을 준다.
-- 서비스 계정의 Flyway가 CREATE TABLE을 실행해도 함수는 소유자(postgres) 권한으로 돈다.
CREATE FUNCTION public.publish_outbox_table() RETURNS event_trigger
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog AS $$
DECLARE
    created record;
BEGIN
    FOR created IN
        SELECT c.object_identity
        FROM pg_event_trigger_ddl_commands() c
        JOIN pg_class t ON t.oid = c.objid
        WHERE c.object_type = 'table' AND t.relname = 'outbox_event'
    LOOP
        EXECUTE format('GRANT SELECT ON TABLE %s TO debezium', created.object_identity);
        EXECUTE format('ALTER PUBLICATION openmarket_outbox ADD TABLE %s', created.object_identity);
        RAISE NOTICE 'publication openmarket_outbox에 % 추가', created.object_identity;
    END LOOP;
END
$$;
REVOKE ALL ON FUNCTION public.publish_outbox_table() FROM PUBLIC;
CREATE EVENT TRIGGER publish_outbox_table ON ddl_command_end
    WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION public.publish_outbox_table();
SQL

# 스키마 안을 들여다볼 수 있게만 한다. 테이블 SELECT는 위 트리거가 outbox_event에만 준다.
mapfile -t SERVICES < <(grep -Ev '^\s*(#|$)' /docker-entrypoint-initdb.d/services.txt)
for schema in "${SERVICES[@]}"; do
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -v schema="$schema" <<'SQL'
GRANT USAGE ON SCHEMA :"schema" TO debezium;
SQL
done
echo "복제 계정 debezium, publication openmarket_outbox, outbox 등록 트리거 생성"
