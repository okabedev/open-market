-- JWT 서명 키(docs/architecture/auth.md "토큰"). 개인 키는 AES-GCM으로 암호화해 저장한다(암호화 키는 .env).
-- 가장 최근에 만든 ACTIVE 키로 서명하고, JWK 세트에는 RETIRED가 아닌 키를 모두 싣는다.
CREATE TABLE signing_key (
    id          varchar(64)  PRIMARY KEY,
    algorithm   varchar(16)  NOT NULL,
    public_key  text         NOT NULL,
    private_key text         NOT NULL,
    status      varchar(16)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    retired_at  timestamptz
);
CREATE INDEX signing_key_status_idx ON signing_key (status, created_at);
