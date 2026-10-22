-- 하위 주문 클레임(docs/architecture/order.md "하위 주문 클레임"). 마지막 클레임만 둔다. 클레임이 없으면 비어 있다.
ALTER TABLE seller_order
    ADD COLUMN claim_id         uuid,
    ADD COLUMN claim_type       varchar(10),
    ADD COLUMN claim_status     varchar(10),
    ADD COLUMN claim_updated_at timestamptz;
