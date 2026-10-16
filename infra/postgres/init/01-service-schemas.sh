#!/bin/bash
# 서비스별 스키마와 계정을 만든다. 형식은 docs/architecture/data.md를 따른다.
# postgres 컨테이너가 빈 볼륨으로 처음 뜰 때 한 번만 실행된다. 바꾼 뒤에는 `docker compose down -v`로 볼륨을 지우고 다시 띄운다.
#
# - 스키마 <service>의 소유자는 계정 <service>_app이다. Flyway가 이 계정으로 테이블을 만든다.
# - 다른 계정은 그 스키마에 USAGE 권한이 없어서 다른 서비스 테이블을 읽을 수 없다.
# - 계정의 search_path를 자기 스키마로 둔다.
set -euo pipefail

mapfile -t SERVICES < <(grep -Ev '^\s*(#|$)' /docker-entrypoint-initdb.d/services.txt)

for service in "${SERVICES[@]}"; do
    password_var="${service^^}_DB_PASSWORD"
    password="${!password_var:?${password_var}가 필요하다}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -v schema="$service" -v role="${service}_app" -v password="$password" <<'SQL'
CREATE ROLE :"role" LOGIN PASSWORD :'password';
CREATE SCHEMA :"schema" AUTHORIZATION :"role";
ALTER ROLE :"role" SET search_path = :"schema";
SQL
    echo "스키마 ${service}, 계정 ${service}_app 생성"
done

# public 스키마에는 아무도 쓰지 못하게 한다(PostgreSQL 15부터 기본값이지만 명시한다).
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC;"
