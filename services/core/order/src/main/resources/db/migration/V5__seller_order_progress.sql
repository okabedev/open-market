-- 하위 주문 진행 상태(docs/architecture/order.md "하위 주문 진행 상태"). 결제 전에는 비어 있다.
ALTER TABLE seller_order
    ADD COLUMN progress            varchar(20),
    ADD COLUMN progress_updated_at timestamptz;
-- 이미 결제된 주문의 하위 주문은 결제 완료로 시작한다.
UPDATE seller_order so
SET progress = 'PAID', progress_updated_at = o.paid_at
FROM orders o
WHERE o.id = so.order_id AND o.status = 'PAID';
