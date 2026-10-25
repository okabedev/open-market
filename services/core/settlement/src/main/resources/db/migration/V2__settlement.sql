-- 정산(docs/architecture/settlement.md "정산 확정"). 판매자·정산 기간마다 하나이고 다시 계산하지 않는다.
CREATE TABLE settlement (
    id                     uuid        PRIMARY KEY,
    seller_id              uuid        NOT NULL,
    period_start           timestamptz NOT NULL,
    period_end             timestamptz NOT NULL,
    item_count             integer     NOT NULL CHECK (item_count > 0),
    sales_amount           bigint      NOT NULL CHECK (sales_amount >= 0),
    commission_amount      bigint      NOT NULL CHECK (commission_amount >= 0),
    seller_coupon_discount bigint      NOT NULL CHECK (seller_coupon_discount >= 0),
    -- 정산 금액. 0 이하일 수 있다
    amount                 bigint      NOT NULL,
    status                 varchar(16) NOT NULL,
    confirmed_at           timestamptz NOT NULL,
    paid_out_at            timestamptz,
    paid_out_by            uuid,
    updated_at             timestamptz NOT NULL,
    version                bigint      NOT NULL,
    -- 판매자·기간마다 한 번만 확정한다(STL-4). 판매자 정산 목록도 이 인덱스로 읽는다
    CONSTRAINT settlement_seller_period_key UNIQUE (seller_id, period_start),
    CHECK (period_end > period_start)
);
-- 관리자 정산 목록(최근 확정 순, 상태로 거르기)
CREATE INDEX settlement_confirmed_idx ON settlement (confirmed_at DESC, id);

ALTER TABLE settlement_item
    ADD CONSTRAINT settlement_item_settlement_fk FOREIGN KEY (settlement_id) REFERENCES settlement (id);
-- 정산 작업: 끝난 기간의 확정되지 않은 판매자·기간
CREATE INDEX settlement_item_closable_idx
    ON settlement_item (period_start, seller_id) WHERE settlement_id IS NULL;
-- 판매자 정산 예정 내역(구매확정 최근 순)
CREATE INDEX settlement_item_pending_idx
    ON settlement_item (seller_id, confirmed_at DESC, id) WHERE settlement_id IS NULL;
-- 정산의 내역(구매확정 최근 순)
CREATE INDEX settlement_item_settlement_idx
    ON settlement_item (settlement_id, confirmed_at DESC, id) WHERE settlement_id IS NOT NULL;
