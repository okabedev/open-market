package com.openmarket.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.fulfillment.command.application.AcceptShipmentCommand;
import com.openmarket.fulfillment.command.application.AcceptShipmentUseCase;
import com.openmarket.fulfillment.command.application.CreateShipmentsCommand;
import com.openmarket.fulfillment.command.application.CreateShipmentsUseCase;
import com.openmarket.fulfillment.command.application.ShipShipmentCommand;
import com.openmarket.fulfillment.command.application.ShipShipmentUseCase;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 발송 전 취소 → payment의 환불 결과(Debezium과 같은 형식) → 클레임 완료(docs/architecture/fulfillment.md "발송 전 취소",
 * "환불 결과 반영"). 환불 결과 페이로드는 발행자(payment)의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    CancelClaimIntegrationTest.Topics.class
})
class CancelClaimIntegrationTest {

    static final String TOPIC = "payment.payment";
    static final Path EXAMPLES = Path.of("src/test/resources/events");
    static final Path PUBLISHER_EXAMPLES = Path.of("../payment/src/test/resources/events");

    @TestConfiguration(proxyBeanMethods = false)
    static class Topics {
        @Bean
        NewTopic paymentPayment() {
            return TopicBuilder.name(TOPIC).partitions(1).build();
        }

        @Bean
        NewTopic paymentPaymentDlt() {
            return TopicBuilder.name(TOPIC + ".DLT").partitions(1).build();
        }
    }

    @Autowired MockMvc mockMvc;

    @Autowired CreateShipmentsUseCase create;

    @Autowired AcceptShipmentUseCase accept;

    @Autowired ShipShipmentUseCase ship;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Autowired KafkaContainer kafka;

    KafkaProducer<String, String> producer;

    final UUID sellerId = UUID.randomUUID();

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
    @DisplayName(
            "발송 전 취소는 204이고 다시 보내도 클레임 이벤트는 한 번이다. 환불 실패는 코드만 남고, 환불 완료면 ClaimCompleted를 한 번 낸다")
    void cancelsAndCompletes() throws Exception {
        CreateShipmentsCommand command = paid();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        UUID buyerId = command.buyerId();
        accept.execute(new AcceptShipmentCommand(sellerId, sellerOrderId));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            post("/v1/seller-orders/{id}/cancel", sellerOrderId)
                                    .with(FulfillmentTokens.buyer(buyerId)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(
                        post("/v1/seller/seller-orders/{id}/ship", sellerOrderId)
                                .with(FulfillmentTokens.seller(sellerId))
                                .contentType("application/json")
                                .content(
                                        "{\"carrier\":\"LOGEN\",\"trackingNumber\":\"1234567890\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.shipmentStatus").value("CANCELLED"));

        assertThat(outboxTypes(sellerOrderId)).containsExactly("ClaimRequested", "ClaimApproved");
        assertSameFields(sellerOrderId, "ClaimRequested");
        assertSameFields(sellerOrderId, "ClaimApproved");
        UUID claimId =
                jdbc.sql("SELECT id FROM claim WHERE seller_order_id = :id")
                        .param("id", sellerOrderId)
                        .query(UUID.class)
                        .single();

        mockMvc.perform(
                        get("/v1/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.shipments[0].cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.claims[0].claimId").value(claimId.toString()))
                .andExpect(jsonPath("$.claims[0].type").value("CANCEL"))
                .andExpect(jsonPath("$.claims[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.claims[0].reason").value("BUYER_CANCEL"))
                .andExpect(jsonPath("$.claims[0].refundAmount").value(19_900));

        UUID failed = UUID.randomUUID();
        send(failed, "RefundFailed", command.orderId(), sellerOrderId, claimId);
        awaitProcessed(failed);
        assertThat(claimColumn(claimId, "refund_failure_code")).isEqualTo("NOT_CANCELABLE_AMOUNT");

        UUID completed = UUID.randomUUID();
        send(completed, "RefundCompleted", command.orderId(), sellerOrderId, claimId);
        send(completed, "RefundCompleted", command.orderId(), sellerOrderId, claimId);
        UUID again = UUID.randomUUID();
        send(again, "RefundCompleted", command.orderId(), sellerOrderId, claimId);
        awaitProcessed(again);

        assertThat(claimColumn(claimId, "status")).isEqualTo("COMPLETED");
        assertThat(claimColumn(claimId, "refund_failure_code")).isNull();
        assertThat(outboxTypes(sellerOrderId)).containsOnlyOnce("ClaimCompleted");
        assertSameFields(sellerOrderId, "ClaimCompleted");
        CreateShipmentsCommand.Line line = command.sellerOrders().getFirst().lines().getFirst();
        assertThat(json.readTree(payload(sellerOrderId, "ClaimCompleted")).get("restockLines"))
                .isEqualTo(
                        json.readTree(
                                "[{\"skuId\":\""
                                        + line.skuId()
                                        + "\",\"quantity\":"
                                        + line.quantity()
                                        + "}]"));
        mockMvc.perform(
                        get("/v1/seller/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.seller(sellerId)))
                .andExpect(jsonPath("$.claims[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.claims[0].completedAt").isNotEmpty());
    }

    @Test
    @DisplayName("발송한 뒤 취소는 409 FULFILLMENT_INVALID_STATE, 다른 구매자는 404다")
    void rejects() throws Exception {
        CreateShipmentsCommand command = paid();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        accept.execute(new AcceptShipmentCommand(sellerId, sellerOrderId));
        ship.execute(new ShipShipmentCommand(sellerId, sellerOrderId, "LOGEN", "1234567890"));

        mockMvc.perform(
                        post("/v1/seller-orders/{id}/cancel", sellerOrderId)
                                .with(FulfillmentTokens.buyer(command.buyerId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_STATE"))
                .andExpect(jsonPath("$.shipmentStatus").value("SHIPPED"));
        mockMvc.perform(
                        post("/v1/seller-orders/{id}/cancel", sellerOrderId)
                                .with(FulfillmentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_NOT_FOUND"));
        assertThat(outboxTypes(sellerOrderId)).doesNotContain("ClaimRequested");
    }

    private CreateShipmentsCommand paid() {
        CreateShipmentsCommand command =
                SellerFulfillmentQueryApiIntegrationTest.command(
                        UUID.randomUUID(), UUID.randomUUID(), sellerId);
        create.execute(command);
        return command;
    }

    private void send(UUID eventId, String type, UUID orderId, UUID sellerOrderId, UUID claimId)
            throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(Files.readString(PUBLISHER_EXAMPLES.resolve(type + ".json")));
        payload.put("orderId", orderId.toString());
        payload.put("sellerOrderId", sellerOrderId.toString());
        payload.put("claimId", claimId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, orderId.toString(), json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", type.getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-22T00:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
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

    private String claimColumn(UUID claimId, String column) {
        return jdbc.sql("SELECT " + column + " FROM claim WHERE id = :id")
                .param("id", claimId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private List<String> outboxTypes(UUID sellerOrderId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_type = 'fulfillment.claim'"
                                + " AND aggregate_id = :id ORDER BY occurred_at, type DESC")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .list();
    }

    private String payload(UUID sellerOrderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = :type")
                .param("id", sellerOrderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }

    private void assertSameFields(UUID sellerOrderId, String type) throws Exception {
        assertThat(json.readTree(payload(sellerOrderId, type)).propertyNames())
                .isEqualTo(
                        json.readTree(Files.readString(EXAMPLES.resolve(type + ".json")))
                                .propertyNames());
    }
}
