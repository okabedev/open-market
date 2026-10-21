package com.openmarket.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.fulfillment.command.application.CreateShipmentsUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** 판매자 발주 확인과 발송(docs/architecture/fulfillment.md "발주 확인과 발송", "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class SellerShippingApiIntegrationTest {

    static final Path EXAMPLES = Path.of("src/test/resources/events");

    @Autowired MockMvc mockMvc;

    @Autowired CreateShipmentsUseCase create;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    final UUID sellerId = UUID.randomUUID();
    final UUID sellerOrderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        create.execute(
                SellerFulfillmentQueryApiIntegrationTest.command(
                        UUID.randomUUID(), sellerOrderId, sellerId));
    }

    @Test
    @DisplayName("발주 확인 → 발송. 같은 요청을 다시 보내도 204이고 이벤트는 하나씩이다. 조회에 송장과 시각이 나온다")
    void acceptsAndShips() throws Exception {
        accept(sellerId).andExpect(status().isNoContent());
        accept(sellerId).andExpect(status().isNoContent());
        ship(sellerId, "CJ_LOGISTICS", "123456789012").andExpect(status().isNoContent());
        ship(sellerId, "CJ_LOGISTICS", "123456789012").andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/v1/seller/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipments[0].carrier").value("CJ_LOGISTICS"))
                .andExpect(jsonPath("$.shipments[0].trackingNumber").value("123456789012"))
                .andExpect(jsonPath("$.shipments[0].acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.shipments[0].shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.shipments[0].deliveredAt").doesNotExist());
        assertThat(outboxTypes()).containsExactly("ShipmentAccepted", "ShipmentShipped");
        for (String type : List.of("ShipmentAccepted", "ShipmentShipped")) {
            assertThat(json.readTree(outboxPayload(type)).propertyNames())
                    .isEqualTo(
                            json.readTree(Files.readString(EXAMPLES.resolve(type + ".json")))
                                    .propertyNames());
        }
    }

    @Test
    @DisplayName("발주 확인 전 발송은 409 FULFILLMENT_INVALID_STATE이고 지금 상태를 준다")
    void shipBeforeAccept() throws Exception {
        ship(sellerId, "CJ_LOGISTICS", "123456789012")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_STATE"))
                .andExpect(jsonPath("$.shipmentStatus").value("READY"));
        assertThat(outboxTypes()).isEmpty();
    }

    @Test
    @DisplayName("택배사나 송장 번호가 틀리면 400 FULFILLMENT_INVALID_INPUT, 값이 없으면 400")
    void invalidInput() throws Exception {
        accept(sellerId).andExpect(status().isNoContent());

        ship(sellerId, "UNKNOWN", "123456789012")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_INPUT"));
        ship(sellerId, "CJ_LOGISTICS", "1234-5678-90")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_INPUT"));
        mockMvc.perform(
                        post("/v1/seller/seller-orders/{id}/ship", sellerOrderId)
                                .with(FulfillmentTokens.seller(sellerId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다른 판매자의 하위 주문은 404, 판매자가 아니면 403")
    void otherSeller() throws Exception {
        accept(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_NOT_FOUND"));
        mockMvc.perform(
                        post("/v1/seller/seller-orders/{id}/accept", sellerOrderId)
                                .with(FulfillmentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        assertThat(outboxTypes()).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions accept(UUID seller)
            throws Exception {
        return mockMvc.perform(
                post("/v1/seller/seller-orders/{id}/accept", sellerOrderId)
                        .with(FulfillmentTokens.seller(seller)));
    }

    private org.springframework.test.web.servlet.ResultActions ship(
            UUID seller, String carrier, String trackingNumber) throws Exception {
        return mockMvc.perform(
                post("/v1/seller/seller-orders/{id}/ship", sellerOrderId)
                        .with(FulfillmentTokens.seller(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"carrier\":\""
                                        + carrier
                                        + "\",\"trackingNumber\":\""
                                        + trackingNumber
                                        + "\"}"));
    }

    private List<String> outboxTypes() {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_id = :id ORDER BY"
                                + " occurred_at, id")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .list();
    }

    private String outboxPayload(String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = :type")
                .param("id", sellerOrderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }
}
