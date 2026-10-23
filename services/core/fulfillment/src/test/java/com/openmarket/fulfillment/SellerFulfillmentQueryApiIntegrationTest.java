package com.openmarket.fulfillment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.fulfillment.command.application.CreateShipmentsCommand;
import com.openmarket.fulfillment.command.application.CreateShipmentsUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 판매자 하위 주문의 배송 조회(docs/architecture/fulfillment.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class SellerFulfillmentQueryApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired CreateShipmentsUseCase create;

    @Test
    @DisplayName("본인 하위 주문의 배송 진행 상태와 배송 목록을 준다")
    void detail() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID sellerOrderId = UUID.randomUUID();
        create.execute(command(orderId, sellerOrderId, sellerId));

        mockMvc.perform(
                        get("/v1/seller/seller-orders/{sellerOrderId}", sellerOrderId)
                                .with(FulfillmentTokens.seller(sellerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerOrderId").value(sellerOrderId.toString()))
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.shipments.length()").value(1))
                .andExpect(jsonPath("$.shipments[0].shipmentId").isNotEmpty())
                .andExpect(jsonPath("$.shipments[0].status").value("READY"));
    }

    @Test
    @DisplayName("다른 판매자의 하위 주문이나 아직 배송이 없는 하위 주문은 404 FULFILLMENT_NOT_FOUND")
    void notFound() throws Exception {
        UUID sellerOrderId = UUID.randomUUID();
        create.execute(command(UUID.randomUUID(), sellerOrderId, UUID.randomUUID()));

        mockMvc.perform(
                        get("/v1/seller/seller-orders/{sellerOrderId}", sellerOrderId)
                                .with(FulfillmentTokens.seller(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_NOT_FOUND"));
        mockMvc.perform(
                        get("/v1/seller/seller-orders/{sellerOrderId}", UUID.randomUUID())
                                .with(FulfillmentTokens.seller(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("판매자 역할이 없으면 403, 토큰이 없으면 401")
    void requiresSeller() throws Exception {
        mockMvc.perform(
                        get("/v1/seller/seller-orders/{sellerOrderId}", UUID.randomUUID())
                                .with(FulfillmentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/seller/seller-orders/{sellerOrderId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    static CreateShipmentsCommand command(UUID orderId, UUID sellerOrderId, UUID sellerId) {
        return new CreateShipmentsCommand(
                orderId,
                UUID.randomUUID(),
                Instant.parse("2026-10-21T03:04:05Z"),
                List.of(
                        new CreateShipmentsCommand.SellerOrder(
                                sellerOrderId,
                                sellerId,
                                19_900,
                                0,
                                19_900,
                                List.of(
                                        new CreateShipmentsCommand.Line(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                "반팔 티셔츠",
                                                List.of("화이트", "M"),
                                                UUID.randomUUID(),
                                                19_900,
                                                1,
                                                19_900)))));
    }
}
