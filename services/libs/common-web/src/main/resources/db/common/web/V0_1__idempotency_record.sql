-- common-web 멱등성 키 기록. 형식은 docs/architecture/data.md를 따른다.
CREATE TABLE idempotency_record (
    principal       varchar(100) NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    request_hash    char(64)     NOT NULL,
    status          varchar(20)  NOT NULL,
    response_status int,
    response_body   jsonb,
    created_at      timestamptz  NOT NULL,
    PRIMARY KEY (principal, idempotency_key)
);

CREATE INDEX idx_idempotency_record_created_at ON idempotency_record (created_at);
