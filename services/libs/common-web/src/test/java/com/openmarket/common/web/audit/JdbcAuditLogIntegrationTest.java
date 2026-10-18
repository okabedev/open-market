package com.openmarket.common.web.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.common.core.audit.AuditEntry;
import com.openmarket.common.core.audit.AuditLog;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
        properties =
                "spring.flyway.locations=classpath:db/common/web,classpath:db/common/messaging")
@Import(PostgresContainerConfiguration.class)
class JdbcAuditLogIntegrationTest {

    @Autowired AuditLog auditLog;

    @Autowired JdbcClient jdbc;

    @Autowired TransactionTemplate transaction;

    @Test
    @DisplayName("감사 로그는 누가, 언제, 무엇에, 어떤 작업을 했는지 남긴다")
    void recordsEntry() {
        String target = UUID.randomUUID().toString();

        auditLog.record(new AuditEntry("admin-1", "SELLER_SUSPENDED", "SELLER", target, "약관 위반"));

        Map<String, Object> row =
                jdbc.sql("SELECT * FROM audit_log WHERE target_id = :target")
                        .param("target", target)
                        .query()
                        .singleRow();
        assertThat(row)
                .containsEntry("actor_id", "admin-1")
                .containsEntry("action", "SELLER_SUSPENDED")
                .containsEntry("target_type", "SELLER")
                .containsEntry("detail", "약관 위반");
        assertThat(row.get("id")).isNotNull();
        assertThat(row.get("occurred_at")).isNotNull();
    }

    @Test
    @DisplayName("작업 트랜잭션이 롤백되면 감사 로그도 남지 않는다")
    void rollsBackWithWork() {
        String target = UUID.randomUUID().toString();

        transaction.executeWithoutResult(
                status -> {
                    auditLog.record(
                            new AuditEntry("admin-1", "SELLER_SUSPENDED", "SELLER", target, null));
                    status.setRollbackOnly();
                });

        assertThat(
                        jdbc.sql("SELECT count(*) FROM audit_log WHERE target_id = :target")
                                .param("target", target)
                                .query(Integer.class)
                                .single())
                .isZero();
    }
}
