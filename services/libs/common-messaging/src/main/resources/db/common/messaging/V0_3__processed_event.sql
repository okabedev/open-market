-- common-messaging 멱등 소비 기록. 같은 이벤트를 같은 리스너가 두 번 처리하지 않게 한다.
CREATE TABLE processed_event (
    event_id     uuid         NOT NULL,
    consumer     varchar(100) NOT NULL,
    processed_at timestamptz  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);

CREATE INDEX idx_processed_event_processed_at ON processed_event (processed_at);
