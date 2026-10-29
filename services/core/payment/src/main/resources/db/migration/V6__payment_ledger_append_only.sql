-- 결제 원장은 추가만 한다(PAY-4, NFR-8). 서비스 계정이 스키마 소유자라 권한으로는 막지 못하므로 고치기·지우기·비우기를 트리거로 거절한다.
-- 앱 버그나 잘못 쓴 정리 작업을 막는다. 소유자가 트리거를 지우는 것은 막지 못한다.
CREATE FUNCTION payment_ledger_entry_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'payment_ledger_entry는 추가만 한다(%)', TG_OP;
END;
$$;

CREATE TRIGGER payment_ledger_entry_no_update_delete
    BEFORE UPDATE OR DELETE ON payment_ledger_entry
    FOR EACH ROW EXECUTE FUNCTION payment_ledger_entry_append_only();

CREATE TRIGGER payment_ledger_entry_no_truncate
    BEFORE TRUNCATE ON payment_ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION payment_ledger_entry_append_only();
