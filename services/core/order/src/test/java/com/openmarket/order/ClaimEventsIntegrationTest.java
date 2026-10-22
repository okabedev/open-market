package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.order.command.domain.Order;
import com.openmarket.order.command.domain.OrderItem;
import com.openmarket.order.command.domain.OrderRepository;
import com.openmarket.order.command.domain.ShippingAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * fulfillment가 낸 클레임 이벤트(Debezium과 같은 형식) → 리스너 → 하위 주문 클레임과 진행 상태(docs/architecture/order.md "하위
 * 주문 클레임"). 페이로드는 발행자(fulfillment)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ClaimEventsIntegrationTest.Topics.class
})
class ClaimEventsIntegrationTest {

    static final String CLAIM_TOPIC = "fulfillment.claim";
    static final String SHIPMENT_TOPIC = "fulfillment.shipment";
    static final Path PUBLISHER_EXAMPLES = Path.of("../fulfillment/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic fulfillmentClaim() {
            return TopicBuilder.name(CLAIM_TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic fulfillmentClaimDlt() {
            return TopicBuilder.name(CLAIM_TOPIC + ".DLT").partitions(1).build();
        }

        @Bean
        NewTopic fulfillmentShipment() {
            return TopicBuilder.name(SHIPMENT_TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic fulfillmentShipmentDlt() {
            return TopicBuilder.name(SHIPMENT_TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    @Autowired OrderRepository orders;

    @Autowired TransactionTemplate transaction;

    KafkaProducer<String, String> producer;

    @BeforeEach
    void setUp() {
        producer =
                new KafkaProducer<>(
                        Map.of(
                                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                kafka.getBootstrapServers()),
                        new StringSerializer(),
                        new StringSerializer());
    }

    @AfterEach
    void tearDown() {
        producer.close();
    }

    @Test
    @DisplayName("발송 전 취소의 클레임 이벤트로 클레임 상태가 바뀌고, 환불이 끝나면 진행 상태가 CANCELLED다. 늦게 온 배송 이벤트는 바꾸지 않는다")
    void appliesCancelClaim() throws Exception {
        Order order = paidOrder();
        UUID sellerOrderId = order.sellerOrders().getFirst().id();
        UUID claimId = UUID.randomUUID();

        send(CLAIM_TOPIC, UUID.randomUUID(), "ClaimRequested", order, sellerOrderId, claimId);
        UUID approved = UUID.randomUUID();
        send(CLAIM_TOPIC, approved, "ClaimApproved", order, sellerOrderId, claimId);
        awaitProcessed(approved);
        assertThat(column(sellerOrderId, "claim_status")).isEqualTo("APPROVED");
        assertThat(column(sellerOrderId, "progress")).isEqualTo("PAID");
        assertThat(hasOngoingOrders(order)).isTrue();

        UUID completed = UUID.randomUUID();
        send(CLAIM_TOPIC, completed, "ClaimCompleted", order, sellerOrderId, claimId);
        send(CLAIM_TOPIC, completed, "ClaimCompleted", order, sellerOrderId, claimId);
        UUID accepted = UUID.randomUUID();
        send(SHIPMENT_TOPIC, accepted, "ShipmentAccepted", order, sellerOrderId, claimId);
        awaitProcessed(completed);
        awaitProcessed(accepted);

        assertThat(column(sellerOrderId, "claim_id")).isEqualTo(claimId.toString());
        assertThat(column(sellerOrderId, "claim_type")).isEqualTo("CANCEL");
        assertThat(column(sellerOrderId, "claim_status")).isEqualTo("COMPLETED");
        assertThat(column(sellerOrderId, "progress")).isEqualTo("CANCELLED");
        assertThat(hasOngoingOrders(order)).isFalse();
        mockMvc.perform(get("/v1/orders/{id}", order.id()).with(OrderTokens.buyer(order.buyerId())))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.sellerOrders[0].progress").value("CANCELLED"))
                .andExpect(jsonPath("$.sellerOrders[0].claim.type").value("CANCEL"))
                .andExpect(jsonPath("$.sellerOrders[0].claim.status").value("COMPLETED"));
        assertThat(
                        jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = :id")
                                .param("id", order.id().toString())
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    @DisplayName("반품 거부는 REJECTED이고 진행 중이다. 새 반품이 환불까지 끝나면 진행 상태가 RETURNED이고 끝난 주문이다")
    void appliesReturnClaims() throws Exception {
        Order order = paidOrder();
        UUID sellerOrderId = order.sellerOrders().getFirst().id();
        UUID rejected = UUID.randomUUID();
        send(
                CLAIM_TOPIC,
                UUID.randomUUID(),
                "ClaimRequested",
                order,
                sellerOrderId,
                rejected,
                "RETURN");
        UUID rejectedEvent = UUID.randomUUID();
        send(CLAIM_TOPIC, rejectedEvent, "ClaimRejected", order, sellerOrderId, rejected, "RETURN");
        awaitProcessed(rejectedEvent);
        assertThat(column(sellerOrderId, "claim_status")).isEqualTo("REJECTED");
        assertThat(hasOngoingOrders(order)).isTrue();

        UUID mediation = UUID.randomUUID();
        send(
                CLAIM_TOPIC,
                mediation,
                "ClaimMediationRequested",
                order,
                sellerOrderId,
                rejected,
                "RETURN");
        awaitProcessed(mediation);
        assertThat(column(sellerOrderId, "claim_status")).isEqualTo("MEDIATING");
        UUID finalRejection = UUID.randomUUID();
        send(
                CLAIM_TOPIC,
                finalRejection,
                "ClaimRejected",
                order,
                sellerOrderId,
                rejected,
                "RETURN");
        awaitProcessed(finalRejection);
        assertThat(column(sellerOrderId, "claim_status")).isEqualTo("REJECTED");

        UUID returned = UUID.randomUUID();
        send(
                CLAIM_TOPIC,
                UUID.randomUUID(),
                "ClaimRequested",
                order,
                sellerOrderId,
                returned,
                "RETURN");
        send(
                CLAIM_TOPIC,
                UUID.randomUUID(),
                "ClaimApproved",
                order,
                sellerOrderId,
                returned,
                "RETURN");
        UUID completed = UUID.randomUUID();
        send(CLAIM_TOPIC, completed, "ClaimCompleted", order, sellerOrderId, returned, "RETURN");
        awaitProcessed(completed);

        assertThat(column(sellerOrderId, "claim_id")).isEqualTo(returned.toString());
        assertThat(column(sellerOrderId, "progress")).isEqualTo("RETURNED");
        assertThat(hasOngoingOrders(order)).isFalse();
    }

    private Order paidOrder() {
        Order order =
                Order.place(
                        UUID.randomUUID(),
                        List.of(
                                new OrderItem(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "반팔 티셔츠",
                                        UUID.randomUUID(),
                                        "길동상회",
                                        UUID.randomUUID(),
                                        List.of(),
                                        List.of(),
                                        19_900,
                                        1)),
                        ShippingAddress.of("홍길동", "010-1234-5678", "06236", "주소", null),
                        Instant.now());
        order.awaitPayment(Instant.now());
        order.pay(19_900, Instant.now(), Instant.now());
        transaction.executeWithoutResult(status -> orders.save(order));
        return order;
    }

    private boolean hasOngoingOrders(Order order) throws Exception {
        String body =
                mockMvc.perform(
                                get("/internal/v1/buyers/{id}/ongoing-orders", order.buyerId())
                                        .with(OrderTokens.internal()))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).get("hasOngoingOrders").asBoolean();
    }

    private String column(UUID sellerOrderId, String column) {
        return jdbc.sql("SELECT " + column + "::text FROM seller_order WHERE id = :id")
                .param("id", sellerOrderId)
                .query(String.class)
                .single();
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.sql(
                                                                "SELECT count(*) FROM processed_event"
                                                                        + " WHERE event_id = :id")
                                                        .param("id", eventId)
                                                        .query(Integer.class)
                                                        .single())
                                        .isEqualTo(1));
    }

    private void send(
            String topic, UUID eventId, String type, Order order, UUID sellerOrderId, UUID claimId)
            throws Exception {
        send(topic, eventId, type, order, sellerOrderId, claimId, "CANCEL");
    }

    private void send(
            String topic,
            UUID eventId,
            String type,
            Order order,
            UUID sellerOrderId,
            UUID claimId,
            String claimType)
            throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json")));
        payload.put("orderId", order.id().toString());
        payload.put("sellerOrderId", sellerOrderId.toString());
        if (payload.has("claimId")) {
            payload.put("claimId", claimId.toString());
            payload.put("type", claimType);
        }
        ProducerRecord<String, String> record =
                new ProducerRecord<>(
                        topic, sellerOrderId.toString(), json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-22T00:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }
}
