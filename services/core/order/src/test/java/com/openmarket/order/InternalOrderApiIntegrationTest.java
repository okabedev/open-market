package com.openmarket.order;

import static com.openmarket.order.CatalogStub.sku;
import static com.openmarket.order.OrderTokens.buyer;
import static com.openmarket.order.OrderTokens.internal;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** 진행 중 주문 조회 내부 API(docs/architecture/order.md "내부 API"). member 탈퇴(ACC-4)가 부른다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class InternalOrderApiIntegrationTest {

    static final String ONGOING = "/internal/v1/buyers/{buyerId}/ongoing-orders";

    @DynamicPropertySource
    static void catalog(DynamicPropertyRegistry registry) {
        CatalogStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JsonMapper json;

    @Autowired JdbcClient jdbc;

    UUID buyerId;
    UUID shirt;

    @BeforeEach
    void givenCatalog() {
        CatalogStub.reset();
        buyerId = UUID.randomUUID();
        shirt = UUID.randomUUID();
        CatalogStub.skus(List.of(sku(shirt, UUID.randomUUID(), "길동상회", "ON_SALE", 19_900, 50)));
    }

    @Test
    @DisplayName("결제 전·결제된 주문이 있으면 진행 중이고, 취소된 주문만 있거나 주문이 없으면 아니다")
    void ongoingOrders() throws Exception {
        expectOngoing(false);

        UUID orderId = place();
        for (String status : List.of("PENDING", "AWAITING_PAYMENT", "PAID")) {
            markStatus(orderId, status);
            expectOngoing(true);
        }

        markStatus(orderId, "CANCELLED");
        expectOngoing(false);
    }

    @Test
    @DisplayName("사용자 토큰으로는 부를 수 없다(403)")
    void internalScopeRequired() throws Exception {
        mockMvc.perform(get(ONGOING, buyerId).with(buyer(buyerId)))
                .andExpect(status().isForbidden());
    }

    private void expectOngoing(boolean ongoing) throws Exception {
        mockMvc.perform(get(ONGOING, buyerId).with(internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasOngoingOrders").value(ongoing));
    }

    private void markStatus(UUID orderId, String status) {
        jdbc.sql("UPDATE orders SET status = :status WHERE id = :id")
                .param("status", status)
                .param("id", orderId)
                .update();
    }

    private UUID place() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "lines",
                        List.of(
                                Map.of(
                                        "skuId",
                                        shirt.toString(),
                                        "quantity",
                                        1,
                                        "expectedPrice",
                                        19_900)),
                        "shipping",
                        Map.of(
                                "recipientName", "홍길동",
                                "recipientPhone", "010-1234-5678",
                                "zipCode", "06236",
                                "baseAddress", "서울 강남구 테헤란로 1"));
        String response =
                mockMvc.perform(
                                post("/v1/orders")
                                        .with(buyer(buyerId))
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json.writeValueAsString(body)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(json.readTree(response).get("orderId").asString());
    }
}
