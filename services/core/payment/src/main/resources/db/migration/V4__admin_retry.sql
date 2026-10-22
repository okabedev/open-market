-- 관리자 재시도(docs/architecture/payment.md "관리자 재시도"). 보상 취소의 토스 시도 번호(Idempotency-Key에 들어간다).
ALTER TABLE payment ADD COLUMN cancel_attempt integer NOT NULL DEFAULT 1;
-- 관리자 목록이 실패한 환불과 토스가 거절한 보상 취소를 오래된 순으로 찾는다.
CREATE INDEX refund_failed_idx ON refund (requested_at) WHERE status = 'FAILED';
CREATE INDEX payment_cancel_failed_idx ON payment (cancel_requested_at)
    WHERE status = 'APPROVED' AND cancel_failure_code IS NOT NULL;
