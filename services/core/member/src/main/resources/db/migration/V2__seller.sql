-- 입점 신청과 판매자(SEL-1~3). 계좌 번호는 애플리케이션에서 암호화한 값과 마스킹한 값만 둔다(data.md "개인정보").
CREATE TABLE seller_application (
    id                       uuid          PRIMARY KEY,
    member_id                uuid          NOT NULL REFERENCES member (id),
    store_name               varchar(30)   NOT NULL,
    business_number          varchar(12)   NOT NULL,
    representative_name      varchar(50)   NOT NULL,
    bank_name                varchar(20)   NOT NULL,
    account_holder           varchar(50)   NOT NULL,
    account_number_encrypted varchar(200)  NOT NULL,
    account_number_masked    varchar(16)   NOT NULL,
    status                   varchar(20)   NOT NULL,
    reject_reason            varchar(500),
    reviewed_by              uuid,
    reviewed_at              timestamptz,
    submitted_at             timestamptz   NOT NULL,
    version                  bigint        NOT NULL
);

CREATE INDEX seller_application_member_id_idx ON seller_application (member_id);
-- 관리자 심사 목록: 상태별로 오래된 신청부터.
CREATE INDEX seller_application_status_submitted_at_idx ON seller_application (status, submitted_at);
-- 회원당 심사 중인 신청은 하나다. 유스케이스가 회원 행을 잠가 먼저 막고, 이 인덱스가 마지막으로 막는다.
CREATE UNIQUE INDEX seller_application_one_pending ON seller_application (member_id) WHERE status = 'SUBMITTED';

CREATE TABLE seller (
    id                       uuid          PRIMARY KEY REFERENCES member (id),
    application_id           uuid          NOT NULL REFERENCES seller_application (id),
    store_name               varchar(30)   NOT NULL,
    business_number          varchar(12)   NOT NULL,
    representative_name      varchar(50)   NOT NULL,
    bank_name                varchar(20)   NOT NULL,
    account_holder           varchar(50)   NOT NULL,
    account_number_encrypted varchar(200)  NOT NULL,
    account_number_masked    varchar(16)   NOT NULL,
    status                   varchar(20)   NOT NULL,
    suspend_reason           varchar(500),
    approved_at              timestamptz   NOT NULL,
    updated_at               timestamptz   NOT NULL,
    version                  bigint        NOT NULL
);

-- 관리자 판매자 목록: 상태별로 최근 승인 순.
CREATE INDEX seller_status_approved_at_idx ON seller (status, approved_at);
