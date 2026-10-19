-- 판매 가능 여부(docs/glossary.md "판매 가능 여부"). 상품마다 한 행. 바뀔 때마다 revision을 올린다.
CREATE TABLE product_availability (
    product_id uuid        PRIMARY KEY REFERENCES product (id) ON DELETE CASCADE,
    in_stock   boolean     NOT NULL,
    revision   bigint      NOT NULL,
    updated_at timestamptz NOT NULL
);
-- 이미 등록된 상품은 지금 재고로 채운다.
INSERT INTO product_availability (product_id, in_stock, revision, updated_at)
SELECT p.id, coalesce(bool_or(s.on_hand - s.reserved > 0), false), 1, now()
FROM product p
LEFT JOIN stock s ON s.product_id = p.id
GROUP BY p.id;

-- 재고 조정 이력(docs/glossary.md "재고 조정"). 만든 뒤 바꾸지 않는다.
CREATE TABLE stock_adjustment (
    id            uuid         PRIMARY KEY,
    sku_id        uuid         NOT NULL REFERENCES sku (id) ON DELETE CASCADE,
    product_id    uuid         NOT NULL,
    delta         integer      NOT NULL CHECK (delta <> 0),
    on_hand_after integer      NOT NULL CHECK (on_hand_after >= 0),
    reason        varchar(100),
    created_at    timestamptz  NOT NULL
);
-- SKU별 이력(최근 순)
CREATE INDEX stock_adjustment_sku_created_idx ON stock_adjustment (sku_id, created_at DESC);
