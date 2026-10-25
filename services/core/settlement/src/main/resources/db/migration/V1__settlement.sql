-- 수수료율(docs/architecture/settlement.md "수수료율"). 소분류 카테고리마다 하나. 없으면 기본 요율이다.
CREATE TABLE commission_rate (
    category_id uuid        PRIMARY KEY,
    rate_bp     integer     NOT NULL CHECK (rate_bp BETWEEN 0 AND 10000),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    version     bigint      NOT NULL
);

-- 정산 대상(docs/architecture/settlement.md "정산 대상"). 구매확정된 하위 주문마다 하나이고 바뀌지 않는다.
CREATE TABLE settlement_item (
    id                     uuid        PRIMARY KEY,
    seller_order_id        uuid        NOT NULL UNIQUE,
    order_id               uuid        NOT NULL,
    seller_id              uuid        NOT NULL,
    confirmed_at           timestamptz NOT NULL,
    period_start           timestamptz NOT NULL,
    period_end             timestamptz NOT NULL,
    sales_amount           bigint      NOT NULL CHECK (sales_amount >= 0),
    commission_amount      bigint      NOT NULL CHECK (commission_amount >= 0),
    seller_coupon_discount bigint      NOT NULL CHECK (seller_coupon_discount >= 0),
    -- 판매 금액 − 수수료 − 판매자 쿠폰 부담분. 0 이하일 수 있다
    settlement_amount      bigint      NOT NULL,
    -- 확정한 정산(정산 확정 PR에서 채운다)
    settlement_id          uuid,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    version                bigint      NOT NULL,
    CHECK (period_end > period_start)
);
-- 판매자·기간의 확정되지 않은 대상(정산 확정, 정산 예정 내역)
CREATE INDEX settlement_item_unsettled_idx
    ON settlement_item (seller_id, period_start) WHERE settlement_id IS NULL;

-- 정산 대상의 주문 항목. 받은 때의 요율과 수수료를 고정한다
CREATE TABLE settlement_item_line (
    settlement_item_id uuid         NOT NULL REFERENCES settlement_item (id),
    position           integer      NOT NULL,
    order_line_id      uuid         NOT NULL,
    product_id         uuid         NOT NULL,
    product_name       varchar(100) NOT NULL,
    category_id        uuid         NOT NULL,
    quantity           integer      NOT NULL CHECK (quantity > 0),
    amount             bigint       NOT NULL CHECK (amount >= 0),
    commission_rate_bp integer      NOT NULL CHECK (commission_rate_bp BETWEEN 0 AND 10000),
    commission_amount  bigint       NOT NULL CHECK (commission_amount >= 0),
    PRIMARY KEY (settlement_item_id, position)
);
