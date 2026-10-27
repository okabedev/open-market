-- common-messaging DLT 메시지(docs/architecture/data.md "dead_letter", ADR 0022). 리스너 그룹이 처리하지 못해 DLT로 간 메시지를
-- DLT 수집기가 모은다. 관리자가 다시 보내거나 버린다.
CREATE TABLE dead_letter (
    id                 uuid         PRIMARY KEY,
    consumer_group     varchar(100) NOT NULL,
    dlt_topic          varchar(255) NOT NULL,
    dlt_partition      integer      NOT NULL,
    dlt_offset         bigint       NOT NULL,
    original_topic     varchar(255) NOT NULL,
    original_partition integer      NOT NULL,
    original_offset    bigint       NOT NULL,
    -- 헤더 id, type. 읽을 수 없으면 비어 있다
    event_id           uuid,
    event_type         varchar(100),
    message_key        text,
    -- 원래 값. API로 내보내지 않는다(개인정보가 들어갈 수 있다)
    payload            text,
    -- 원래 헤더와 kafka_dlt-* 헤더. 값은 UTF-8 문자열, 읽을 수 없으면 base64
    headers            jsonb        NOT NULL,
    exception_class    varchar(300),
    trace_id           varchar(32),
    status             varchar(20)  NOT NULL CHECK (status IN ('PENDING', 'RETRIED', 'DISCARDED')),
    dead_lettered_at   timestamptz  NOT NULL,
    resolved_at        timestamptz,
    resolved_by        varchar(100),
    discard_reason     varchar(500),
    -- 수집기가 같은 DLT 메시지를 다시 읽어도 한 행이다
    UNIQUE (dlt_topic, dlt_partition, dlt_offset)
);

-- 관리자 목록(쌓인 시각 내림차순, 커서)과 상태별 목록
CREATE INDEX dead_letter_dead_lettered_idx ON dead_letter (dead_lettered_at, id);
CREATE INDEX dead_letter_status_idx ON dead_letter (status, dead_lettered_at);
-- 같은 이벤트의 행(다시 보낸 메시지가 또 실패한 경우)을 함께 보여 준다
CREATE INDEX dead_letter_event_idx ON dead_letter (event_id);
