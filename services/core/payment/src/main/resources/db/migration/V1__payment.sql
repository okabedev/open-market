-- 결제(docs/architecture/payment.md "결제"). 주문 하나당 하나다.
CREATE TABLE payment (
    id              uuid         PRIMARY KEY,
    order_id        uuid         NOT NULL UNIQUE,
    buyer_id        uuid         NOT NULL,
    amount          bigint       NOT NULL CHECK (amount >= 0),
    order_name      varchar(100) NOT NULL,
    status          varchar(20)  NOT NULL,
    -- 마지막 승인 요청의 토스 결제 키와 시도 번호(토스 Idempotency-Key), 요청 시각
    payment_key     varchar(200),
    confirm_attempt integer      NOT NULL DEFAULT 0,
    requested_at    timestamptz,
    method          varchar(50),
    approved_at     timestamptz,
    failure_code    varchar(100),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL
);
-- 재조회 작업(PaymentReconcileJob)이 승인 결과를 기다리는 결제를 오래된 순으로 찾는다.
CREATE INDEX payment_in_progress_idx ON payment (requested_at) WHERE status = 'IN_PROGRESS';

-- 결제 원장(PAY-4). 토스 요청 하나마다 한 줄을 추가하고 고치거나 지우지 않는다. 응답 원문은 저장하지 않는다.
CREATE TABLE payment_ledger_entry (
    id              uuid         PRIMARY KEY,
    payment_id      uuid         NOT NULL REFERENCES payment (id),
    kind            varchar(20)  NOT NULL,
    outcome         varchar(20)  NOT NULL,
    pg_code         varchar(100),
    payment_key     varchar(200) NOT NULL,
    transaction_key varchar(200),
    amount          bigint,
    occurred_at     timestamptz  NOT NULL
);
CREATE INDEX payment_ledger_entry_payment_idx ON payment_ledger_entry (payment_id, occurred_at);
