-- 결제 전 취소된 주문의 결제 무효화와 보상 취소(docs/architecture/payment.md "보상 취소").
-- 취소 요청 시각, 취소된 시각, 토스가 전액 취소를 거절했을 때의 오류 코드
ALTER TABLE payment
    ADD COLUMN cancel_requested_at timestamptz,
    ADD COLUMN cancelled_at        timestamptz,
    ADD COLUMN cancel_failure_code varchar(100);

-- 취소 작업(PaymentCancelJob)이 토스 전액 취소를 기다리는 결제를 오래된 요청 순으로 찾는다.
CREATE INDEX payment_cancel_requested_idx ON payment (cancel_requested_at)
    WHERE status = 'APPROVED' AND cancel_requested_at IS NOT NULL AND cancel_failure_code IS NULL;
