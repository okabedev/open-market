package com.openmarket.common.core.audit;

/**
 * 감사 로그(NFR-8, docs/glossary.md "감사 로그"). 관리자 작업을 누가, 언제, 무엇에 했는지 남긴다.
 *
 * <p>유스케이스가 작업과 같은 트랜잭션에서 부른다. 작업이 롤백되면 기록도 남지 않는다. 구현은 {@code common-web}이 서비스 스키마의 {@code
 * audit_log} 테이블에 쓴다.
 */
public interface AuditLog {

    void record(AuditEntry entry);
}
