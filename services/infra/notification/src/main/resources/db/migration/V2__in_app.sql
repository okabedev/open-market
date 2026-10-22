-- 인앱 알림(docs/architecture/notification.md "인앱 알림"). 받는 쪽(앱)의 알림함에 들어가고, 관리자 알림은 받는 사람 없이 역할에 한 건이다.
ALTER TABLE notification ALTER COLUMN recipient_account_id DROP NOT NULL;
ALTER TABLE notification ADD COLUMN audience varchar(16);
ALTER TABLE notification ADD COLUMN message varchar(500);
ALTER TABLE notification ADD COLUMN target_type varchar(32);
ALTER TABLE notification ADD COLUMN target_id uuid;
ALTER TABLE notification ADD CONSTRAINT notification_recipient_ck CHECK (
    CASE channel
        WHEN 'IN_APP' THEN audience IS NOT NULL AND message IS NOT NULL
            AND (audience = 'ADMIN') = (recipient_account_id IS NULL)
        ELSE recipient_account_id IS NOT NULL
    END);

-- 이벤트 하나가 받는 사람마다 알림을 만들 수 있다(OrderPaid → 구매자, 판매자들).
DROP INDEX notification_source_channel_uk;
CREATE UNIQUE INDEX notification_source_channel_uk
    ON notification (source_event_id, channel, audience, recipient_account_id) NULLS NOT DISTINCT;
-- 알림함 목록과 안 읽은 수. 관리자 알림함은 recipient_account_id IS NULL로 찾는다.
CREATE INDEX notification_inbox_idx
    ON notification (audience, recipient_account_id, created_at DESC, id DESC) WHERE channel = 'IN_APP';
-- 보관 기간이 지난 알림 정리(NotificationPurgeJob)
CREATE INDEX notification_created_idx ON notification (created_at);

-- 알림 읽음. 관리자 알림은 관리자마다 따로 읽는다.
CREATE TABLE notification_read (
    notification_id uuid        NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    account_id      uuid        NOT NULL,
    read_at         timestamptz NOT NULL,
    PRIMARY KEY (notification_id, account_id)
);
