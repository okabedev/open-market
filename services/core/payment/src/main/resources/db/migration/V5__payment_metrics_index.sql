-- 관리자 결제 지표(docs/architecture/payment.md "운영 지표"). 기간 안의 승인 시도(CONFIRM)를 찾는다.
CREATE INDEX payment_ledger_entry_kind_occurred_idx ON payment_ledger_entry (kind, occurred_at);
