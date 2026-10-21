package com.openmarket.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.payment.command.application.PreparePaymentCommand;
import com.openmarket.payment.command.application.PreparePaymentUseCase;
import com.openmarket.payment.infra.RefundJob;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * fulfillment가 낸 {@code ClaimApproved}(Debezium과 같은 형식) → 환불 요청 → 환불 작업 → 토스 부분취소(WireMock)
 * (docs/architecture/payment.md "환불"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID와 금액만 바꾼다.
 */
@SpringBootTest(properties = "openmarket.payment.refund-interval=1h")
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    RefundIntegrationTest.Topics.class
})
class RefundIntegrationTest {

    static final String TOPIC = "fulfillment.claim";
    static final Path PUBLISHER_EXAMPLES = Path.of("../fulfillment/src/test/resources/events");
    static final Path EVENTS = Path.of("src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic fulfillmentClaim() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic fulfillmentClaimDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @DynamicPropertySource
    static void toss(DynamicPropertyRegistry registry) {
        TossStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired PreparePaymentUseCase prepare;

    @Autowired RefundJob refundJob;

    KafkaProducer<String, String> producer;

    final UUID buyerId = UUID.randomUUID();
    final UUID orderId = UUID.randomUUID();
    final String paymentKey = "tgen_" + UUID.randomUUID().toString().replace("-", "");

    @BeforeEach
    void setUp() throws Exception {
        TossStub.reset();
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));
        TossStub.confirmReturns(200, TossStub.payment(paymentKey, orderId, 59_700, "DONE"));
        mockMvc.perform(
                        post("/v1/payments/confirm")
                                .with(PaymentTokens.buyer(buyerId))
                                .header("Idempotency-Key", "confirm-" + orderId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"orderId\":\""
                                                + orderId
                                                + "\",\"paymentKey\":\""
                                                + paymentKey
                                                + "\",\"amount\":59700}"))
                .andExpect(status().isOk());
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName(
            "ClaimApproved를 받으면 환불을 만들고, 환불 작업이 토스 부분취소 후 RefundCompleted를 낸다. 같은 이벤트·교환은 환불이 없다")
    void refundsApprovedClaim() throws Exception {
        UUID claimId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String approved = claimApproved(claimId, "RETURN", 13_900);
        send(eventId, approved);
        send(eventId, approved);
        send(UUID.randomUUID(), claimApproved(UUID.randomUUID(), "EXCHANGE", 0));
        UUID last = UUID.randomUUID();
        send(last, claimApproved(claimId, "RETURN", 13_900));

        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT count(*) FROM processed_event"
                                                                        + " WHERE event_id = :id")
                                                        .param("id", last)
                                                        .query(Integer.class)
                                                        .single())
                                        .isEqualTo(1));
        assertThat(refunds()).containsExactly("REQUESTED 13900");

        TossStub.cancelReturns(
                paymentKey,
                200,
                TossStub.cancelledPayment(paymentKey, orderId, 13_900, "PARTIAL_CANCELED", 45_800));
        assertThat(refundJob.refund()).isEqualTo(1);
        assertThat(refundJob.refund()).isZero();

        assertThat(refunds()).containsExactly("COMPLETED 13900");
        assertThat(
                        jdbc.sql(
                                        "SELECT status || ' ' || refunded_amount FROM payment WHERE"
                                                + " order_id = :id")
                                .param("id", orderId)
                                .query(String.class)
                                .single())
                .isEqualTo("PARTIALLY_CANCELLED 13900");
        assertThat(ledger()).endsWith("REFUND CANCELLED PARTIAL_CANCELED 13900");
        assertThat(outboxTypes()).containsExactly("PaymentApproved", "RefundCompleted");
        assertThat(json.readTree(outboxPayload("RefundCompleted")).propertyNames())
                .isEqualTo(example("RefundCompleted").propertyNames());
    }

    @Test
    @DisplayName("토스가 환불을 거절하면 FAILED와 RefundFailed를 남기고 결제는 그대로다")
    void rejected() throws Exception {
        UUID eventId = UUID.randomUUID();
        send(eventId, claimApproved(UUID.randomUUID(), "CANCEL", 59_700));
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(refunds()).containsExactly("REQUESTED 59700"));
        TossStub.cancelReturns(
                paymentKey, 403, TossStub.error("NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다."));

        refundJob.refund();

        assertThat(refunds()).containsExactly("FAILED 59700");
        assertThat(outboxTypes()).containsExactly("PaymentApproved", "RefundFailed");
        assertThat(json.readTree(outboxPayload("RefundFailed")).propertyNames())
                .isEqualTo(example("RefundFailed").propertyNames());
        assertThat(
                        jdbc.sql("SELECT status FROM payment WHERE order_id = :id")
                                .param("id", orderId)
                                .query(String.class)
                                .single())
                .isEqualTo("APPROVED");
    }

    private String claimApproved(UUID claimId, String type, long amount) throws Exception {
        ObjectNode node =
                (ObjectNode)
                        json.readTree(
                                Files.readString(PUBLISHER_EXAMPLES.resolve("ClaimApproved.json")));
        node.put("claimId", claimId.toString());
        node.put("orderId", orderId.toString());
        node.put("type", type);
        node.put("refundAmount", amount);
        return json.writeValueAsString(node);
    }

    private void send(UUID eventId, String payload) throws Exception {
        String sellerOrderId = json.readTree(payload).get("sellerOrderId").asString();
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, sellerOrderId, payload);
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", "ClaimApproved".getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-23T05:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    private List<String> refunds() {
        return jdbc.sql(
                        "SELECT status || ' ' || amount FROM refund WHERE order_id = :id ORDER BY"
                                + " requested_at")
                .param("id", orderId)
                .query(String.class)
                .list();
    }

    private List<String> ledger() {
        return jdbc.sql(
                        "SELECT l.kind || ' ' || l.outcome || ' ' || l.pg_code || coalesce(' ' ||"
                                + " l.amount, '') FROM payment_ledger_entry l JOIN payment p ON"
                                + " p.id = l.payment_id WHERE p.order_id = :id ORDER BY"
                                + " l.occurred_at")
                .param("id", orderId)
                .query(String.class)
                .list();
    }

    private List<String> outboxTypes() {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_id = :id ORDER BY"
                                + " occurred_at, id")
                .param("id", orderId.toString())
                .query(String.class)
                .list();
    }

    private String outboxPayload(String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = :type")
                .param("id", orderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }

    private tools.jackson.databind.JsonNode example(String type) throws Exception {
        return json.readTree(Files.readString(EVENTS.resolve(type + ".json")));
    }
}
