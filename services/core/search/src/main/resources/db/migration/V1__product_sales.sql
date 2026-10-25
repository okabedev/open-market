-- 판매량(docs/architecture/search.md "판매량"). 상품의 구매확정 수량 합. 더한 결과와 리비전을 검색 문서에 쓴다.
CREATE TABLE product_sales (
    product_id  uuid        PRIMARY KEY,
    sales_count bigint      NOT NULL CHECK (sales_count >= 0),
    revision    bigint      NOT NULL,
    updated_at  timestamptz NOT NULL
);

-- 센 하위 주문. 같은 구매확정이 다른 이벤트 ID로 다시 와도 한 번만 더한다.
CREATE TABLE product_sales_order (
    seller_order_id uuid        PRIMARY KEY,
    counted_at      timestamptz NOT NULL
);
