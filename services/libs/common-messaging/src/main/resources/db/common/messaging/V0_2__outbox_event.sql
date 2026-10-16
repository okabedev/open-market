-- common-messaging 발행 대기 이벤트. 형식은 docs/architecture/data.md를 따른다.
-- Debezium이 INSERT를 읽어 Kafka로 보낸다. 발행된 행은 정리 작업이 보관 기간 뒤에 지운다.
CREATE TABLE outbox_event (
    id             uuid         PRIMARY KEY,
    aggregate_type varchar(100) NOT NULL,
    aggregate_id   varchar(100) NOT NULL,
    type           varchar(100) NOT NULL,
    payload        jsonb        NOT NULL,
    schema_version int          NOT NULL,
    traceparent    varchar(55),
    occurred_at    timestamptz  NOT NULL
);

CREATE INDEX idx_outbox_event_occurred_at ON outbox_event (occurred_at);
