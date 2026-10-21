package com.openmarket.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.fulfillment.command.application.AcceptShipmentCommand;
import com.openmarket.fulfillment.command.application.AcceptShipmentUseCase;
import com.openmarket.fulfillment.command.application.CreateShipmentsCommand;
import com.openmarket.fulfillment.command.application.CreateShipmentsUseCase;
import com.openmarket.fulfillment.command.application.ShipShipmentCommand;
import com.openmarket.fulfillment.command.application.ShipShipmentUseCase;
import com.openmarket.fulfillment.infra.PurchaseConfirmJob;
import com.openmarket.fulfillment.infra.ShipmentSimulatorJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * 구매자 배송 조회와 구매확정, 자동 구매확정(docs/architecture/fulfillment.md "구매확정"). 시뮬레이터 지연과 확정 기한을 0으로 두고 작업을 직접
 * 부른다.
 */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "openmarket.fulfillment.simulator.in-transit-after=0s",
            "openmarket.fulfillment.simulator.deliver-after=0s",
            "openmarket.fulfillment.simulator.interval=1h",
            "openmarket.fulfillment.purchase-confirm.auto-confirm-after=0s",
            "openmarket.fulfillment.purchase-confirm.interval=1h"
        })
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class PurchaseConfirmIntegrationTest {

    static final Path EXAMPLES = Path.of("src/test/resources/events");

    @Autowired MockMvc mockMvc;

    @Autowired CreateShipmentsUseCase create;

    @Autowired AcceptShipmentUseCase accept;

    @Autowired ShipShipmentUseCase ship;

    @Autowired ShipmentSimulatorJob simulator;

    @Autowired PurchaseConfirmJob confirmJob;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    final UUID sellerId = UUID.randomUUID();

    @Test
    @DisplayName("구매자는 배송 완료 뒤 구매확정한다. 다시 해도 204이고 이벤트는 하나다. 조회에 확정 기한·시각·방식이 나온다")
    void buyerConfirms() throws Exception {
        CreateShipmentsCommand command = paid();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        UUID buyerId = command.buyerId();

        mockMvc.perform(
                        post("/v1/seller-orders/{id}/confirm", sellerOrderId)
                                .with(FulfillmentTokens.buyer(buyerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_INVALID_STATE"))
                .andExpect(jsonPath("$.shipmentStatus").value("READY"));

        deliver(sellerOrderId);
        mockMvc.perform(
                        get("/v1/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipments[0].trackingNumber").value("1234567890"))
                .andExpect(jsonPath("$.confirmDueAt").isNotEmpty())
                .andExpect(jsonPath("$.confirmedAt").doesNotExist());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            post("/v1/seller-orders/{id}/confirm", sellerOrderId)
                                    .with(FulfillmentTokens.buyer(buyerId)))
                    .andExpect(status().isNoContent());
        }
        // 기한(0초)이 지났어도 이미 확정한 하위 주문은 자동 확정하지 않는다.
        confirmJob.confirm();

        mockMvc.perform(
                        get("/v1/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.buyer(buyerId)))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.confirmMode").value("BUYER"));
        assertThat(outboxTypes(sellerOrderId)).containsOnlyOnce("PurchaseConfirmed");
        assertThat(json.readTree(confirmedPayload(sellerOrderId)).propertyNames())
                .isEqualTo(
                        json.readTree(Files.readString(EXAMPLES.resolve("PurchaseConfirmed.json")))
                                .propertyNames());
    }

    @Test
    @DisplayName("다른 구매자는 조회·확정 모두 404, 판매자 경로와 섞이지 않는다")
    void otherBuyer() throws Exception {
        UUID sellerOrderId = paid().sellerOrders().getFirst().sellerOrderId();

        mockMvc.perform(
                        get("/v1/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_NOT_FOUND"));
        mockMvc.perform(
                        post("/v1/seller-orders/{id}/confirm", sellerOrderId)
                                .with(FulfillmentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("확정 기한이 지난 배송 완료 하위 주문은 자동 구매확정된다. 다시 돌려도 한 번이다")
    void autoConfirms() throws Exception {
        UUID sellerOrderId = paid().sellerOrders().getFirst().sellerOrderId();
        deliver(sellerOrderId);

        confirmJob.confirm();
        confirmJob.confirm();

        assertThat(
                        jdbc.sql(
                                        "SELECT mode FROM purchase_confirmation WHERE"
                                                + " seller_order_id = :id")
                                .param("id", sellerOrderId)
                                .query(String.class)
                                .list())
                .containsExactly("AUTO");
        assertThat(outboxTypes(sellerOrderId)).containsOnlyOnce("PurchaseConfirmed");
        assertThat(json.readTree(confirmedPayload(sellerOrderId)).get("mode").asString())
                .isEqualTo("AUTO");
    }

    private CreateShipmentsCommand paid() {
        CreateShipmentsCommand command =
                SellerFulfillmentQueryApiIntegrationTest.command(
                        UUID.randomUUID(), UUID.randomUUID(), sellerId);
        create.execute(command);
        return command;
    }

    private void deliver(UUID sellerOrderId) {
        accept.execute(new AcceptShipmentCommand(sellerId, sellerOrderId));
        ship.execute(new ShipShipmentCommand(sellerId, sellerOrderId, "LOGEN", "1234567890"));
        simulator.simulate();
        simulator.simulate();
    }

    private List<String> outboxTypes(UUID sellerOrderId) {
        return jdbc.sql("SELECT type FROM outbox_event WHERE aggregate_id = :id")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .list();
    }

    private String confirmedPayload(UUID sellerOrderId) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = 'PurchaseConfirmed'")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .single();
    }
}
