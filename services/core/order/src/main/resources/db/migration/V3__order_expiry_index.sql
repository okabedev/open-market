-- 결제 기한 만료 작업(docs/architecture/order.md "만료와 결제 전 취소"). 결제 전 주문만 담는 부분 인덱스다.
CREATE INDEX orders_unpaid_placed_idx ON orders (placed_at)
    WHERE status IN ('PENDING', 'AWAITING_PAYMENT');
