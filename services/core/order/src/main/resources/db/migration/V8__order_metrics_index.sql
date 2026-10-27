-- 관리자 주문 지표(docs/architecture/order.md "운영 지표"). 주문 수는 주문 시각, 결제된 주문 수와 GMV는 결제 시각으로 센다.
CREATE INDEX orders_placed_idx ON orders (placed_at);
CREATE INDEX orders_paid_idx ON orders (paid_at) WHERE paid_at IS NOT NULL;
