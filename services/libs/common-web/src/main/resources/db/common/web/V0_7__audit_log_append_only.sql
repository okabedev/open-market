-- 감사 로그는 추가만 한다(NFR-8, docs/architecture/data.md "audit_log"). 서비스 계정이 스키마 소유자라 권한으로는 막지 못하므로
-- 고치기·지우기·비우기를 트리거로 거절한다. 앱 버그나 잘못 쓴 정리 작업을 막는다. 소유자가 트리거를 지우는 것은 막지 못한다.
CREATE FUNCTION audit_log_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_log는 추가만 한다(%)', TG_OP;
END;
$$;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_append_only();
