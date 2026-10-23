-- 주문 쿠폰과 예약 결과 합류(docs/architecture/order.md "쿠폰 적용", "예약 결과").
-- 받은 예약 결과는 결제 전에만 쓴다. 이미 있는 주문은 쿠폰이 없어 재고 결과 하나로 정해진다.
ALTER TABLE orders
    ADD COLUMN platform_issued_coupon_id uuid,
    ADD COLUMN stock_reserved            boolean NOT NULL DEFAULT false,
    ADD COLUMN coupon_reserved           boolean NOT NULL DEFAULT false;

-- 하위 주문의 쿠폰 할인 스냅샷. amount는 상품 금액 그대로이고, 결제 금액 = 상품 금액 − 두 할인이다.
ALTER TABLE seller_order
    ADD COLUMN seller_issued_coupon_id  uuid,
    ADD COLUMN seller_coupon_discount   bigint NOT NULL DEFAULT 0 CHECK (seller_coupon_discount >= 0),
    ADD COLUMN platform_coupon_discount bigint NOT NULL DEFAULT 0 CHECK (platform_coupon_discount >= 0),
    ADD COLUMN payment_amount           bigint;

UPDATE seller_order SET payment_amount = amount;

ALTER TABLE seller_order
    ALTER COLUMN payment_amount SET NOT NULL,
    ADD CONSTRAINT seller_order_payment_amount_check
        CHECK (payment_amount >= 0
               AND payment_amount = amount - seller_coupon_discount - platform_coupon_discount);
