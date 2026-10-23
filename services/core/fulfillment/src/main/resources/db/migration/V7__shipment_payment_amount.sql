-- 배송의 판매자 쿠폰 할인과 하위 주문 결제 금액(docs/architecture/fulfillment.md "환불 금액").
-- 환불은 결제 금액에서 계산한다. 이미 있는 배송은 쿠폰이 없어 결제 금액이 상품 금액과 같다.
ALTER TABLE shipment
    ADD COLUMN seller_coupon_discount bigint NOT NULL DEFAULT 0 CHECK (seller_coupon_discount >= 0),
    ADD COLUMN payment_amount         bigint;

UPDATE shipment SET payment_amount = amount;

ALTER TABLE shipment
    ALTER COLUMN payment_amount SET NOT NULL,
    ADD CONSTRAINT shipment_payment_amount_check
        CHECK (payment_amount >= 0 AND payment_amount + seller_coupon_discount <= amount);
