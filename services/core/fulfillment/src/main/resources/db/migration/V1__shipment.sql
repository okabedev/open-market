-- 배송(docs/architecture/fulfillment.md "배송"). 하위 주문마다 하나이고, 교환 재발송은 같은 하위 주문에 새 배송을 만든다.
CREATE TABLE shipment (
    id              uuid        PRIMARY KEY,
    order_id        uuid        NOT NULL,
    seller_order_id uuid        NOT NULL,
    seller_id       uuid        NOT NULL,
    buyer_id        uuid        NOT NULL,
    -- 교환 재발송이면 그 클레임. OrderPaid로 만든 첫 배송은 비어 있다
    claim_id        uuid,
    -- 하위 주문 금액(원). 환불 금액의 기준이다
    amount          bigint      NOT NULL CHECK (amount >= 0),
    status          varchar(20) NOT NULL,
    paid_at         timestamptz NOT NULL,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    version         bigint      NOT NULL
);
CREATE INDEX shipment_seller_order_idx ON shipment (seller_order_id, created_at);
-- 첫 배송은 하위 주문마다 하나다. 같은 주문의 OrderPaid가 다시 와도 두 번 만들지 않는다.
CREATE UNIQUE INDEX shipment_first_idx ON shipment (seller_order_id) WHERE claim_id IS NULL;

-- 배송 항목. 주문 당시 스냅샷(OrderPaid)이다. 구매확정(주문 항목 단위), 재입고, 화면·알림의 상품명에 쓴다.
CREATE TABLE shipment_line (
    shipment_id    uuid         NOT NULL REFERENCES shipment (id),
    position       integer      NOT NULL,
    order_line_id  uuid         NOT NULL,
    sku_id         uuid         NOT NULL,
    product_id     uuid         NOT NULL,
    product_name   varchar(100) NOT NULL,
    option_value_1 varchar(30),
    option_value_2 varchar(30),
    category_id    uuid         NOT NULL,
    unit_price     bigint       NOT NULL,
    quantity       integer      NOT NULL,
    amount         bigint       NOT NULL,
    PRIMARY KEY (shipment_id, position)
);
