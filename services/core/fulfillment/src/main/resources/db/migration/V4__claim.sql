-- 발송 전 취소(docs/architecture/fulfillment.md "발송 전 취소").
ALTER TABLE shipment ADD COLUMN cancelled_at timestamptz;

-- 클레임(docs/architecture/fulfillment.md "클레임"). 하위 주문 전체가 대상이고, 신청 때 하위 주문의 마지막 배송을 가리킨다.
CREATE TABLE claim (
    id                  uuid        PRIMARY KEY,
    type                varchar(10) NOT NULL,
    status              varchar(10) NOT NULL,
    order_id            uuid        NOT NULL,
    seller_order_id     uuid        NOT NULL,
    seller_id           uuid        NOT NULL,
    buyer_id            uuid        NOT NULL,
    shipment_id         uuid        NOT NULL REFERENCES shipment (id),
    reason              varchar(20) NOT NULL,
    fault               varchar(10) NOT NULL,
    -- 환불 금액(원). 교환은 0
    refund_amount       bigint      NOT NULL CHECK (refund_amount >= 0),
    -- 마지막 환불 실패 코드(RefundFailed). 환불이 끝나면 지운다
    refund_failure_code varchar(60),
    requested_at        timestamptz NOT NULL,
    approved_at         timestamptz,
    completed_at        timestamptz,
    updated_at          timestamptz NOT NULL,
    version             bigint      NOT NULL
);
-- 하위 주문의 클레임 목록(신청 순서)
CREATE INDEX claim_seller_order_idx ON claim (seller_order_id, requested_at);
-- 진행 중인 클레임은 하위 주문마다 하나다. 신청이 겹쳐도 하나만 남는다.
CREATE UNIQUE INDEX claim_ongoing_idx ON claim (seller_order_id)
    WHERE status IN ('REQUESTED', 'APPROVED', 'MEDIATING');
