-- 탈퇴(ACC-4). 탈퇴하면 개인정보를 가명으로 바꾸고 행은 남긴다(거래 기록이 memberId를 가리킨다).
ALTER TABLE member
    ADD COLUMN status       varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN withdrawn_at timestamptz;
