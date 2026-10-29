package com.openmarket.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 결제 원장은 추가만 한다(PAY-4, NFR-8, docs/architecture/payment.md). 서비스 계정이 스키마 소유자라 트리거로 막는다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresContainerConfiguration.class)
class PaymentLedgerAppendOnlyIntegrationTest {

    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("원장 줄은 고치거나 지우거나 비울 수 없다")
    void rejectsUpdateDeleteAndTruncate() {
        UUID paymentId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.sql(
                        "INSERT INTO payment (id, order_id, buyer_id, amount, order_name, status, created_at,"
                                + " updated_at, version) VALUES (:id, :order, :buyer, 10000, '원장 주문',"
                                + " 'IN_PROGRESS', :at, :at, 0)")
                .param("id", paymentId)
                .param("order", UUID.randomUUID())
                .param("buyer", UUID.randomUUID())
                .param("at", now)
                .update();
        jdbc.sql(
                        "INSERT INTO payment_ledger_entry (id, payment_id, kind, outcome, payment_key,"
                                + " amount, occurred_at) VALUES (:id, :payment, 'CONFIRM', 'APPROVED',"
                                + " :key, 10000, :at)")
                .param("id", UUID.randomUUID())
                .param("payment", paymentId)
                .param("key", "tgen_" + paymentId)
                .param("at", now)
                .update();

        assertThatThrownBy(
                        () ->
                                jdbc.sql(
                                                "UPDATE payment_ledger_entry SET amount = 1 WHERE"
                                                        + " payment_id = :p")
                                        .param("p", paymentId)
                                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("payment_ledger_entry");
        assertThatThrownBy(
                        () ->
                                jdbc.sql("DELETE FROM payment_ledger_entry WHERE payment_id = :p")
                                        .param("p", paymentId)
                                        .update())
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("TRUNCATE payment_ledger_entry").update())
                .isInstanceOf(DataAccessException.class);
        assertThat(
                        jdbc.sql(
                                        "SELECT amount FROM payment_ledger_entry WHERE payment_id ="
                                                + " :p")
                                .param("p", paymentId)
                                .query(Long.class)
                                .single())
                .isEqualTo(10_000L);
    }
}
