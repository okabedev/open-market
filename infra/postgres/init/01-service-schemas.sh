#!/bin/bash
# 서비스별 스키마와 계정을 만든다. 형식은 docs/architecture/data.md를 따른다.
# postgres 컨테이너가 빈 볼륨으로 처음 뜰 때 한 번만 실행된다. 바꾼 뒤에는 `docker compose down -v`로 볼륨을 지우고 다시 띄운다.
#
# - 스키마 <service>의 소유자는 마이그레이션 계정 <service>_owner다. Flyway만 이 계정으로 테이블을 만든다(ADR 0027).
# - 앱 계정 <service>_app은 스키마 USAGE와 테이블 SELECT·INSERT·UPDATE·DELETE, 시퀀스 사용만 갖는다. 소유자가 아니므로
#   테이블·트리거를 만들거나 바꾸거나 지우지 못한다(감사 로그·결제 원장의 추가 전용 트리거를 지울 수 없다).
# - owner가 나중에 만드는 테이블·시퀀스에도 같은 권한이 가도록 기본 권한(ALTER DEFAULT PRIVILEGES)을 둔다.
# - 다른 계정은 그 스키마에 USAGE 권한이 없어서 다른 서비스 테이블을 읽을 수 없다.
# - 두 계정의 search_path를 자기 스키마로 둔다.
set -euo pipefail

mapfile -t SERVICES < <(grep -Ev '^\s*(#|$)' /docker-entrypoint-initdb.d/services.txt)

for service in "${SERVICES[@]}"; do
    app_password_var="${service^^}_DB_PASSWORD"
    owner_password_var="${service^^}_DB_OWNER_PASSWORD"
    app_password="${!app_password_var:?${app_password_var}가 필요하다}"
    owner_password="${!owner_password_var:?${owner_password_var}가 필요하다}"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -v schema="$service" -v owner="${service}_owner" -v app="${service}_app" \
        -v owner_password="$owner_password" -v app_password="$app_password" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'owner_password';
CREATE ROLE :"app" LOGIN PASSWORD :'app_password';
CREATE SCHEMA :"schema" AUTHORIZATION :"owner";
ALTER ROLE :"owner" SET search_path = :"schema";
ALTER ROLE :"app" SET search_path = :"schema";
GRANT USAGE ON SCHEMA :"schema" TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA :"schema"
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA :"schema"
    GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL
    echo "스키마 ${service}, 계정 ${service}_owner(소유자)·${service}_app(데이터) 생성"
done

# public 스키마에는 아무도 쓰지 못하게 한다(PostgreSQL 15부터 기본값이지만 명시한다).
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC;"
