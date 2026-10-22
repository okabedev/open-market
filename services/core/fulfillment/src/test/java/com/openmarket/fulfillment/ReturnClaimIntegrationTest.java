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
import com.openmarket.fulfillment.infra.ShipmentSimulatorJob;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 반품·교환 신청 → 판매자 승인·거부 → 환불 결과·교환 재발송(docs/architecture/fulfillment.md "반품·교환 신청" 이하). 시뮬레이터 지연을
 * 0으로 두고 작업을 직접 부른다. 환불 결과는 payment의 JSON 예시를 읽어 ID만 바꾼다.
 */
@SpringBootTest(
        properties = {
            "openmarket.fulfillment.simulator.in-transit-after=0s",
            "openmarket.fulfillment.simulator.deliver-after=0s",
            "openmarket.fulfillment.simulator.interval=1h",
            "openmarket.fulfillment.purchase-confirm.interval=1h"
        })
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ReturnClaimIntegrationTest.Topics.class
})
class ReturnClaimIntegrationTest {

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

    @Autowired ShipmentSimulatorJob simulator;

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
    @DisplayName("구매자 귀책 반품: 신청(같은 키는 같은 응답) → 승인(재입고, 배송비 차감) → 환불 완료면 ClaimCompleted에 재입고 항목이 있다")
    void returns() throws Exception {
        CreateShipmentsCommand command = delivered();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        UUID buyerId = command.buyerId();
        UUID photo = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String body =
                "{\"type\":\"RETURN\",\"reason\":\"CHANGE_OF_MIND\",\"description\":\"생각과"
                        + " 달라요\",\"fileIds\":[\""
                        + photo
                        + "\"]}";

        String first =
                claim(sellerOrderId, buyerId, key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String again =
                claim(sellerOrderId, buyerId, key, body)
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(json.readTree(again)).isEqualTo(json.readTree(first));
        UUID claimId = UUID.fromString(json.readTree(first).get("claimId").asString());
        claim(sellerOrderId, buyerId, UUID.randomUUID().toString(), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_CLAIM_IN_PROGRESS"));
        assertSameFields(sellerOrderId, "ClaimRequested");

        approve(claimId, sellerId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_INPUT"));
        approve(claimId, UUID.randomUUID(), "{\"restock\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_CLAIM_NOT_FOUND"));
        approve(claimId, sellerId, "{\"restock\":true}").andExpect(status().isNoContent());
        approve(claimId, sellerId, "{\"restock\":true}").andExpect(status().isNoContent());
        assertThat(outboxTypes(sellerOrderId)).containsOnlyOnce("ClaimApproved");
        assertThat(
                        json.readTree(payload(sellerOrderId, "ClaimApproved"))
                                .get("refundAmount")
                                .asLong())
                .isEqualTo(13_900);

        UUID refunded = UUID.randomUUID();
        sendRefundCompleted(refunded, command.orderId(), sellerOrderId, claimId);
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(
                        () -> assertThat(outboxTypes(sellerOrderId)).contains("ClaimCompleted"));
        CreateShipmentsCommand.Line line = command.sellerOrders().getFirst().lines().getFirst();
        assertThat(json.readTree(payload(sellerOrderId, "ClaimCompleted")).get("restockLines"))
                .isEqualTo(json.readTree("[{\"skuId\":\"" + line.skuId() + "\",\"quantity\":1}]"));

        mockMvc.perform(
                        get("/v1/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.buyer(buyerId)))
                .andExpect(jsonPath("$.claims[0].type").value("RETURN"))
                .andExpect(jsonPath("$.claims[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.claims[0].description").value("생각과 달라요"))
                .andExpect(jsonPath("$.claims[0].fileIds[0]").value(photo.toString()))
                .andExpect(jsonPath("$.claims[0].restock").value(true))
                .andExpect(jsonPath("$.claims[0].refundAmount").value(13_900));
    }

    @Test
    @DisplayName("교환: 승인하면 새 배송이 생기고, 판매자가 새 배송을 발주 확인·발송하면 클레임이 끝난다")
    void exchanges() throws Exception {
        CreateShipmentsCommand command = delivered();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        claim(
                        sellerOrderId,
                        command.buyerId(),
                        UUID.randomUUID().toString(),
                        "{\"type\":\"EXCHANGE\",\"reason\":\"CHANGE_OF_MIND\",\"description\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_INPUT"));
        String created =
                claim(
                                sellerOrderId,
                                command.buyerId(),
                                UUID.randomUUID().toString(),
                                "{\"type\":\"EXCHANGE\",\"reason\":\"WRONG_ITEM\",\"description\":\"다른"
                                        + " 색이 왔어요\"}")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(created).get("claimId").asString());

        approve(claimId, sellerId, "{}").andExpect(status().isNoContent());
        mockMvc.perform(
                        get("/v1/seller/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.seller(sellerId)))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.shipments.length()").value(2))
                .andExpect(jsonPath("$.claims[0].status").value("APPROVED"));

        accept.execute(new AcceptShipmentCommand(sellerId, sellerOrderId));
        ship.execute(new ShipShipmentCommand(sellerId, sellerOrderId, "HANJIN", "9876543210"));

        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
        assertThat(json.readTree(payload(sellerOrderId, "ClaimCompleted")).get("type").asString())
                .isEqualTo("EXCHANGE");
    }

    @Test
    @DisplayName("거부: 사유를 남기고 ClaimRejected를 낸다. 구매확정 기한을 늦추고, 거부한 클레임은 승인할 수 없고, 다시 신청할 수 있다")
    void rejects() throws Exception {
        CreateShipmentsCommand command = delivered();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        Instant dueBefore = confirmDueAt(sellerOrderId);
        String body = "{\"type\":\"RETURN\",\"reason\":\"DEFECTIVE\",\"description\":\"불량\"}";
        String created =
                claim(sellerOrderId, command.buyerId(), UUID.randomUUID().toString(), body)
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(created).get("claimId").asString());

        reject(claimId, "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        reject(claimId, "{\"reason\":\"사용 흔적이 있습니다\"}").andExpect(status().isNoContent());
        reject(claimId, "{\"reason\":\"사용 흔적이 있습니다\"}").andExpect(status().isNoContent());
        approve(claimId, sellerId, "{\"restock\":true}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_CLAIM_INVALID_STATE"))
                .andExpect(jsonPath("$.claimStatus").value("REJECTED"));

        assertThat(outboxTypes(sellerOrderId)).containsOnlyOnce("ClaimRejected");
        assertSameFields(sellerOrderId, "ClaimRejected");
        assertThat(
                        json.readTree(payload(sellerOrderId, "ClaimRejected"))
                                .get("mediated")
                                .asBoolean())
                .isFalse();
        assertThat(confirmDueAt(sellerOrderId)).isAfter(dueBefore);
        claim(sellerOrderId, command.buyerId(), UUID.randomUUID().toString(), body)
                .andExpect(status().isCreated());
    }

    private CreateShipmentsCommand delivered() {
        CreateShipmentsCommand command =
                SellerFulfillmentQueryApiIntegrationTest.command(
                        UUID.randomUUID(), UUID.randomUUID(), sellerId);
        create.execute(command);
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        accept.execute(new AcceptShipmentCommand(sellerId, sellerOrderId));
        ship.execute(new ShipShipmentCommand(sellerId, sellerOrderId, "LOGEN", "1234567890"));
        simulator.simulate();
        simulator.simulate();
        return command;
    }

    private ResultActions claim(UUID sellerOrderId, UUID buyerId, String key, String body)
            throws Exception {
        return mockMvc.perform(
                post("/v1/seller-orders/{id}/claims", sellerOrderId)
                        .with(FulfillmentTokens.buyer(buyerId))
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(body));
    }

    private ResultActions approve(UUID claimId, UUID seller, String body) throws Exception {
        return mockMvc.perform(
                post("/v1/seller/claims/{id}/approve", claimId)
                        .with(FulfillmentTokens.seller(seller))
                        .contentType("application/json")
                        .content(body));
    }

    private ResultActions reject(UUID claimId, String body) throws Exception {
        return mockMvc.perform(
                post("/v1/seller/claims/{id}/reject", claimId)
                        .with(FulfillmentTokens.seller(sellerId))
                        .contentType("application/json")
                        .content(body));
    }

    private void sendRefundCompleted(UUID eventId, UUID orderId, UUID sellerOrderId, UUID claimId)
            throws Exception {
        ObjectNode payload =
                (ObjectNode)
                        json.readTree(
                                Files.readString(
                                        PUBLISHER_EXAMPLES.resolve("RefundCompleted.json")));
        payload.put("orderId", orderId.toString());
        payload.put("sellerOrderId", sellerOrderId.toString());
        payload.put("claimId", claimId.toString());
        ProducerRecord<String, String> record =
                new ProducerRecord<>(TOPIC, orderId.toString(), json.writeValueAsString(payload));
        record.headers().add("id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("type", "RefundCompleted".getBytes(StandardCharsets.UTF_8));
        record.headers().add("occurredAt", "2026-10-22T00:00:00Z".getBytes(StandardCharsets.UTF_8));
        record.headers().add("schemaVersion", "1".getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    private String claimStatus(UUID claimId) {
        return jdbc.sql("SELECT status FROM claim WHERE id = :id")
                .param("id", claimId)
                .query(String.class)
                .single();
    }

    private Instant confirmDueAt(UUID sellerOrderId) {
        return jdbc.sql("SELECT confirm_due_at FROM shipment WHERE seller_order_id = :id")
                .param("id", sellerOrderId)
                .query(Instant.class)
                .single();
    }

    private List<String> outboxTypes(UUID sellerOrderId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_type = 'fulfillment.claim'"
                                + " AND aggregate_id = :id")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .list();
    }

    private String payload(UUID sellerOrderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = :type ORDER BY occurred_at DESC LIMIT 1")
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
