-- 장바구니(docs/architecture/order.md "장바구니"). 회원당 하나. 상품 정보는 저장하지 않고 볼 때 catalog에서 읽는다.
CREATE TABLE cart (
    member_id  uuid        PRIMARY KEY,
    updated_at timestamptz NOT NULL
);

CREATE TABLE cart_item (
    id        uuid        PRIMARY KEY,
    member_id uuid        NOT NULL REFERENCES cart (member_id),
    sku_id    uuid        NOT NULL,
    quantity  int         NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    added_at  timestamptz NOT NULL,
    UNIQUE (member_id, sku_id)
);
