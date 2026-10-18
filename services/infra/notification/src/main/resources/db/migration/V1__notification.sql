-- 알림(docs/glossary.md "알림"). 원인 이벤트 하나와 채널 하나에 한 행(NTF-2).
CREATE TABLE notification (
    id                   uuid         PRIMARY KEY,
    source_event_id      uuid         NOT NULL,
    channel              varchar(16)  NOT NULL,
    kind                 varchar(40)  NOT NULL,
    recipient_account_id uuid         NOT NULL,
    recipient_email      varchar(320),
    status               varchar(16)  NOT NULL,
    attempts             integer      NOT NULL,
    next_attempt_at      timestamptz  NOT NULL,
    sent_at              timestamptz,
    created_at           timestamptz  NOT NULL,
    version              bigint       NOT NULL
);
CREATE UNIQUE INDEX notification_source_channel_uk ON notification (source_event_id, channel);
-- 발송 작업이 보낼 차례인 메일을 찾는다.
CREATE INDEX notification_due_idx ON notification (next_attempt_at) WHERE status = 'PENDING';

-- 템플릿에 넣을 값. 인증 링크처럼 비밀이 들어갈 수 있어 발송을 끝내면 지운다.
CREATE TABLE notification_param (
    notification_id uuid          NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    name            varchar(64)   NOT NULL,
    value           varchar(2000) NOT NULL,
    PRIMARY KEY (notification_id, name)
);
