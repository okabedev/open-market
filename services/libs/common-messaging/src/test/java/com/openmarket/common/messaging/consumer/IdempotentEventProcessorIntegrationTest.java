package com.openmarket.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.messaging.TestEvents;
import com.openmarket.common.messaging.TestEvents.OrderPlacedMessage;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(PostgresContainerConfiguration.class)
class IdempotentEventProcessorIntegrationTest {

    private static final String LISTENER = "catalog.order-placed";

    @Autowired IdempotentEventProcessor processor;

    @Autowired Outbox outbox;

    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("같은 이벤트가 다시 와도 핸들러는 한 번만 실행된다")
    void processesEachEventOnce() {
        UUID id = UUID.randomUUID();
        List<EventEnvelope<OrderPlacedMessage>> handled = new ArrayList<>();

        boolean first = process(LISTENER, TestEvents.orderPlaced(id, "order-1"), handled::add);
        boolean second = process(LISTENER, TestEvents.orderPlaced(id, "order-1"), handled::add);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(handled)
                .singleElement()
                .satisfies(event -> assertThat(event.id()).isEqualTo(id));
        assertThat(processedCount(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이벤트라도 리스너가 다르면 각각 처리한다")
    void listenersAreIndependent() {
        UUID id = UUID.randomUUID();

        boolean catalog = process("catalog.order-placed", TestEvents.orderPlaced(id, "o"), e -> {});
        boolean promotion =
                process("promotion.order-placed", TestEvents.orderPlaced(id, "o"), e -> {});

        assertThat(catalog).isTrue();
        assertThat(promotion).isTrue();
        assertThat(processedCount(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("핸들러가 실패하면 처리 기록과 outbox 이벤트가 함께 롤백되어 재시도 때 다시 처리한다")
    void failureRollsBackRecordAndOutbox() {
        UUID id = UUID.randomUUID();
        AtomicReference<UUID> appended = new AtomicReference<>();

        assertThatThrownBy(
                        () ->
                                process(
                                        LISTENER,
                                        TestEvents.orderPlaced(id, "order-1"),
                                        event -> {
                                            appended.set(appendStockReserved(event));
                                            throw new IllegalStateException("일시 장애");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(processedCount(id)).isZero();
        assertThat(outboxExists(appended.get())).isFalse();

        boolean retried =
                process(
                        LISTENER,
                        TestEvents.orderPlaced(id, "order-1"),
                        event -> appended.set(appendStockReserved(event)));

        assertThat(retried).isTrue();
        assertThat(processedCount(id)).isEqualTo(1);
        assertThat(outboxExists(appended.get())).isTrue();
    }

    @Test
    @DisplayName("읽을 수 없는 메시지는 핸들러를 부르지 않고 기록도 남기지 않는다")
    void invalidMessageIsNotRecorded() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = TestEvents.record(id, "order-1", "not json");
        List<EventEnvelope<OrderPlacedMessage>> handled = new ArrayList<>();

        assertThatThrownBy(() -> process(LISTENER, record, handled::add))
                .isInstanceOf(InvalidEventException.class);

        assertThat(handled).isEmpty();
        assertThat(processedCount(id)).isZero();
    }

    private boolean process(
            String listener,
            ConsumerRecord<String, String> record,
            Consumer<EventEnvelope<OrderPlacedMessage>> handler) {
        return processor.process(listener, record, OrderPlacedMessage.class, handler);
    }

    private UUID appendStockReserved(EventEnvelope<OrderPlacedMessage> event) {
        return outbox.append(
                "catalog.stock", event.payload().orderId(), "StockReserved", event.payload());
    }

    private int processedCount(UUID eventId) {
        return jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id")
                .param("id", eventId)
                .query(Integer.class)
                .single();
    }

    private boolean outboxExists(UUID id) {
        return jdbc.sql("SELECT count(*) FROM outbox_event WHERE id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                == 1;
    }
}
