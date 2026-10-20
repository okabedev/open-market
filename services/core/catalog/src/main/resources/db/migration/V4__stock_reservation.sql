-- 재고 예약(docs/architecture/catalog.md "재고 예약"). 주문 하나에 하나. 실패한 예약과 예약 없이 취소된 주문도 남긴다.
CREATE TABLE stock_reservation (
    id         uuid        PRIMARY KEY,
    order_id   uuid        NOT NULL UNIQUE,
    status     varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL
);

CREATE TABLE stock_reservation_line (
    reservation_id uuid    NOT NULL REFERENCES stock_reservation (id),
    sku_id         uuid    NOT NULL,
    product_id     uuid    NOT NULL,
    quantity       integer NOT NULL CHECK (quantity > 0)
);
CREATE INDEX stock_reservation_line_reservation_idx ON stock_reservation_line (reservation_id);
