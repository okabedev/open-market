-- 환불(docs/architecture/payment.md "환불"). 결제는 환불로 돌려준 금액의 합을 센다.
ALTER TABLE payment ADD COLUMN refunded_amount bigint NOT NULL DEFAULT 0 CHECK (refunded_amount >= 0);

-- 클레임 하나에 하나다. 같은 ClaimApproved가 다시 와도 환불을 두 번 만들지 않는다.
CREATE TABLE refund (
    id              uuid        PRIMARY KEY,
    claim_id        uuid        NOT NULL UNIQUE,
    -- 결제가 없어 환불할 수 없었으면 비어 있다
    payment_id      uuid        REFERENCES payment (id),
    order_id        uuid        NOT NULL,
    seller_order_id uuid        NOT NULL,
    amount          bigint      NOT NULL CHECK (amount >= 0),
    status          varchar(20) NOT NULL,
    -- 토스 부분취소 시도 번호(토스 Idempotency-Key). 관리자 재시도가 올린다
    attempt         integer     NOT NULL,
    failure_code    varchar(100),
    requested_at    timestamptz NOT NULL,
    completed_at    timestamptz,
    updated_at      timestamptz NOT NULL,
    version         bigint      NOT NULL
);
-- 환불 작업이 토스를 부를 환불을 오래된 순으로 찾는다.
CREATE INDEX refund_requested_idx ON refund (requested_at) WHERE status = 'REQUESTED';
-- 환불 요청이 결제의 진행 중인 환불 금액을 센다.
CREATE INDEX refund_payment_idx ON refund (payment_id);
