package com.openmarket.common.web.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 감사 로그는 추가만 한다(docs/architecture/data.md "audit_log"). 서비스 계정이 스키마 소유자라 권한 대신 트리거로 막는다. */
@SpringBootTest(
        properties =
                "spring.flyway.locations=classpath:db/common/web,classpath:db/common/messaging")
@Import(PostgresContainerConfiguration.class)
class AuditLogAppendOnlyIntegrationTest {

    @Autowired AuditLog auditLog;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("쓴 감사 로그는 고치거나 지우거나 비울 수 없다")
    void rejectsUpdateDeleteAndTruncate() {
        String target = UUID.randomUUID().toString();
        auditLog.record(new AuditEntry("admin-1", "COUPON_STOPPED", "COUPON", target, null));

        assertThatThrownBy(
                        () ->
                                jdbc.sql("UPDATE audit_log SET detail = 'x' WHERE target_id = :t")
                                        .param("t", target)
                                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit_log");
        assertThatThrownBy(
                        () ->
                                jdbc.sql("DELETE FROM audit_log WHERE target_id = :t")
                                        .param("t", target)
                                        .update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE audit_log").update())
                .isInstanceOf(DataAccessException.class);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM audit_log WHERE target_id = :t")
                                .param("t", target)
                                .query(Long.class)
                                .single())
                .isEqualTo(1);
    }
}
