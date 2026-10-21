-- 결제 시각(docs/architecture/order.md "결제 결과"). 토스 승인 시각이다. 결제된 주문만 값이 있다.
ALTER TABLE orders ADD COLUMN paid_at timestamptz;
