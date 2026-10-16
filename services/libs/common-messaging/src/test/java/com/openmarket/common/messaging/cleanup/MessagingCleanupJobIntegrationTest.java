package com.openmarket.common.messaging.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class MessagingCleanupJobIntegrationTest {

    @Autowired MessagingCleanupJob job;

    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("보관 기간이 지난 outbox_event와 processed_event만 지운다")
    void deletesOnlyExpiredRows() {
        UUID expiredOutbox = insertOutbox("8 days");
        UUID keptOutbox = insertOutbox("6 days");
        UUID expiredProcessed = insertProcessed("31 days");
        UUID keptProcessed = insertProcessed("29 days");

        MessagingCleanupJob.Result result = job.cleanUp();

        assertThat(result.outboxEvents()).isGreaterThanOrEqualTo(1);
        assertThat(result.processedEvents()).isGreaterThanOrEqualTo(1);
        assertThat(count("outbox_event", "id", expiredOutbox)).isZero();
        assertThat(count("outbox_event", "id", keptOutbox)).isEqualTo(1);
        assertThat(count("processed_event", "event_id", expiredProcessed)).isZero();
        assertThat(count("processed_event", "event_id", keptProcessed)).isEqualTo(1);
    }

    @Test
    @DisplayName("ShedLock으로 잠근 동안에는 다시 실행해도 건너뛴다")
    void runsOncePerLockPeriod() {
        job.run();
        assertThat(
                        jdbc.sql("SELECT count(*) FROM shedlock WHERE name = :name")
                                .param("name", MessagingCleanupJob.LOCK_NAME)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);

        // 최소 잠금 시간(1분) 안에 다른 인스턴스가 실행한 것과 같다.
        UUID expired = insertOutbox("8 days");
        job.run();

        assertThat(count("outbox_event", "id", expired)).isEqualTo(1);
    }

    private UUID insertOutbox(String age) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO outbox_event (id, aggregate_type, aggregate_id, type, payload, schema_version,"
                                + " occurred_at) VALUES (:id, 'order.order', 'o', 'OrderPlaced', '{}', 1, now() -"
                                + " CAST(:age AS interval))")
                .param("id", id)
                .param("age", age)
                .update();
        return id;
    }

    private UUID insertProcessed(String age) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO processed_event (event_id, consumer, processed_at)"
                                + " VALUES (:id, 'test', now() - CAST(:age AS interval))")
                .param("id", id)
                .param("age", age)
                .update();
        return id;
    }

    private int count(String table, String idColumn, UUID id) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + idColumn + " = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }
}
