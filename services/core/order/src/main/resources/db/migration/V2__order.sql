-- 주문(docs/architecture/order.md "주문"). 테이블 이름은 예약어 order를 피해 orders다.
CREATE TABLE orders (
    id              uuid         PRIMARY KEY,
    buyer_id        uuid         NOT NULL,
    status          varchar(20)  NOT NULL,
    cancel_reason   varchar(30),
    amount          bigint       NOT NULL CHECK (amount >= 0),
    order_name      varchar(100) NOT NULL,
    recipient_name  varchar(50)  NOT NULL,
    recipient_phone varchar(20)  NOT NULL,
    zip_code        varchar(5)   NOT NULL,
    base_address    varchar(200) NOT NULL,
    detail_address  varchar(200),
    placed_at       timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL
);
-- 구매자 주문 목록(최근 순)
CREATE INDEX orders_buyer_placed_idx ON orders (buyer_id, placed_at DESC);

-- 하위 주문(판매자별)
CREATE TABLE seller_order (
    id         uuid         PRIMARY KEY,
    order_id   uuid         NOT NULL REFERENCES orders (id),
    position   integer      NOT NULL,
    seller_id  uuid         NOT NULL,
    store_name varchar(100) NOT NULL,
    amount     bigint       NOT NULL CHECK (amount >= 0)
);
CREATE INDEX seller_order_order_idx ON seller_order (order_id);
CREATE INDEX seller_order_seller_idx ON seller_order (seller_id);

-- 주문 항목. 주문 당시 상품명·옵션·카테고리·가격 스냅샷이다.
CREATE TABLE order_line (
    id              uuid         PRIMARY KEY,
    seller_order_id uuid         NOT NULL REFERENCES seller_order (id),
    position        integer      NOT NULL,
    sku_id          uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    product_name    varchar(100) NOT NULL,
    category_id     uuid         NOT NULL,
    option_name_1   varchar(20),
    option_value_1  varchar(30),
    option_name_2   varchar(20),
    option_value_2  varchar(30),
    unit_price      bigint       NOT NULL CHECK (unit_price >= 0),
    quantity        integer      NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    amount          bigint       NOT NULL CHECK (amount >= 0)
);
CREATE INDEX order_line_seller_order_idx ON order_line (seller_order_id);
