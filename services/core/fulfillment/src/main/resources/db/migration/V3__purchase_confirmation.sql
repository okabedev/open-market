-- 구매확정(docs/architecture/fulfillment.md "구매확정"). 자동 확정 기한은 배송 완료 때 정한다.
ALTER TABLE shipment ADD COLUMN confirm_due_at timestamptz;
-- 자동 확정 작업이 기한이 지난 배송 완료 배송을 찾는다.
CREATE INDEX shipment_confirm_due_idx ON shipment (confirm_due_at) WHERE status = 'DELIVERED';

-- 하위 주문당 한 번이다. 구매자 확정과 자동 확정이 겹쳐도 하나만 남는다.
CREATE TABLE purchase_confirmation (
    id              uuid        PRIMARY KEY,
    seller_order_id uuid        NOT NULL UNIQUE,
    shipment_id     uuid        NOT NULL REFERENCES shipment (id),
    mode            varchar(10) NOT NULL,
    confirmed_at    timestamptz NOT NULL
);
