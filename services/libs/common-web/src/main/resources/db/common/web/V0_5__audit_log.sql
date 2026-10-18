-- 감사 로그(NFR-8). 관리자 작업과 같은 트랜잭션에 쓴다. 지우거나 고치지 않는다. 형식은 docs/architecture/data.md를 따른다.
CREATE TABLE audit_log (
    id          uuid         PRIMARY KEY,
    actor_id    varchar(100) NOT NULL,
    action      varchar(100) NOT NULL,
    target_type varchar(50)  NOT NULL,
    target_id   varchar(100) NOT NULL,
    detail      varchar(1000),
    trace_id    varchar(32),
    occurred_at timestamptz  NOT NULL
);

CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);
