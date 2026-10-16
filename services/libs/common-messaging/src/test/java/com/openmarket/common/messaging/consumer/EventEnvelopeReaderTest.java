package com.openmarket.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.messaging.TestEvents;
import com.openmarket.common.messaging.TestEvents.OrderPlacedMessage;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventEnvelopeReaderTest {

    private final EventEnvelopeReader reader = new EventEnvelopeReader(JsonMapper.shared());

    @Test
    @DisplayName("헤더와 키, 페이로드를 봉투로 묶는다")
    void readsHeadersAndPayload() {
        UUID id = UUID.randomUUID();
        ConsumerRecord<String, String> record = TestEvents.orderPlaced(id, "order-1");
        TestEvents.header(
                record, "traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        EventEnvelope<OrderPlacedMessage> event = reader.read(record, OrderPlacedMessage.class);

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.type()).isEqualTo("OrderPlaced");
        assertThat(event.aggregateId()).isEqualTo("order-1");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse(TestEvents.OCCURRED_AT));
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.traceparent())
                .isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
        assertThat(event.payload()).isEqualTo(new OrderPlacedMessage("order-1", 15000));
    }

    @Test
    @DisplayName("traceparent가 없어도 읽는다")
    void traceparentIsOptional() {
        EventEnvelope<OrderPlacedMessage> event =
                reader.read(
                        TestEvents.orderPlaced(UUID.randomUUID(), "order-1"),
                        OrderPlacedMessage.class);

        assertThat(event.traceparent()).isNull();
    }

    @Test
    @DisplayName("발행자가 추가한 모르는 필드는 무시한다")
    void ignoresUnknownFields() {
        ConsumerRecord<String, String> record =
                TestEvents.record(
                        UUID.randomUUID(),
                        "order-1",
                        "{\"orderId\": \"order-1\", \"totalAmount\": 15000, \"couponId\": \"c-1\"}");

        assertThat(reader.read(record, OrderPlacedMessage.class).payload().orderId())
                .isEqualTo("order-1");
    }

    @Test
    @DisplayName("필수 헤더가 없으면 영구 오류로 거절한다")
    void rejectsMissingHeader() {
        ConsumerRecord<String, String> record =
                TestEvents.orderPlaced(UUID.randomUUID(), "order-1");
        record.headers().remove("id");

        assertThatThrownBy(() -> reader.read(record, OrderPlacedMessage.class))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("헤더 id");
    }

    @Test
    @DisplayName("헤더 형식이 잘못되면 영구 오류로 거절한다")
    void rejectsMalformedHeader() {
        ConsumerRecord<String, String> record =
                TestEvents.orderPlaced(UUID.randomUUID(), "order-1");
        record.headers().remove("schemaVersion");
        TestEvents.header(record, "schemaVersion", "v1");

        assertThatThrownBy(() -> reader.read(record, OrderPlacedMessage.class))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("헤더 형식");
    }

    @Test
    @DisplayName("페이로드를 읽을 수 없으면 본문을 메시지에 남기지 않고 거절한다")
    void rejectsUnreadablePayloadWithoutLeakingIt() {
        ConsumerRecord<String, String> record =
                TestEvents.record(UUID.randomUUID(), "order-1", "{\"orderId\": \"010-1234-5678\"");

        assertThatThrownBy(() -> reader.read(record, OrderPlacedMessage.class))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("OrderPlacedMessage")
                .hasMessageNotContaining("010-1234-5678")
                .hasNoCause();
    }

    @Test
    @DisplayName("키가 없으면 거절한다")
    void rejectsMissingKey() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(TestEvents.TOPIC, 0, 0L, null, "{}");

        assertThatThrownBy(() -> reader.read(record, OrderPlacedMessage.class))
                .isInstanceOf(InvalidEventException.class);
    }
}
