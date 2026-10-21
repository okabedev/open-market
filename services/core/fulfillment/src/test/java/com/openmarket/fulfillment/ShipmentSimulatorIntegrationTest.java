package com.openmarket.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.fulfillment.command.application.AcceptShipmentCommand;
import com.openmarket.fulfillment.command.application.AcceptShipmentUseCase;
import com.openmarket.fulfillment.command.application.CreateShipmentsUseCase;
import com.openmarket.fulfillment.command.application.ShipShipmentCommand;
import com.openmarket.fulfillment.command.application.ShipShipmentUseCase;
import com.openmarket.fulfillment.infra.ShipmentSimulatorJob;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** 배송 시뮬레이터(docs/architecture/fulfillment.md "배송 시뮬레이터"). 지연을 0으로 두고 작업을 직접 부른다. 한 주기에 한 단계만 간다. */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "openmarket.fulfillment.simulator.in-transit-after=0s",
            "openmarket.fulfillment.simulator.deliver-after=0s",
            "openmarket.fulfillment.simulator.interval=1h"
        })
@Import(PostgresContainerConfiguration.class)
class ShipmentSimulatorIntegrationTest {

    static final Path EXAMPLES = Path.of("src/test/resources/events");

    @Autowired CreateShipmentsUseCase create;

    @Autowired AcceptShipmentUseCase accept;

    @Autowired ShipShipmentUseCase ship;

    @Autowired ShipmentSimulatorJob job;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    @Test
    @DisplayName("발송한 배송은 한 주기에 배송 중, 다음 주기에 배송 완료가 되고 단계마다 이벤트를 낸다. 발송 전 배송은 그대로다")
    void advancesOneStepPerRun() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID shipped = UUID.randomUUID();
        UUID notShipped = UUID.randomUUID();
        create.execute(
                SellerFulfillmentQueryApiIntegrationTest.command(
                        UUID.randomUUID(), shipped, sellerId));
        create.execute(
                SellerFulfillmentQueryApiIntegrationTest.command(
                        UUID.randomUUID(), notShipped, sellerId));
        accept.execute(new AcceptShipmentCommand(sellerId, shipped));
        accept.execute(new AcceptShipmentCommand(sellerId, notShipped));
        ship.execute(new ShipShipmentCommand(sellerId, shipped, "LOTTE", "1234567890"));

        job.simulate();
        assertThat(status(shipped)).isEqualTo("IN_TRANSIT");
        job.simulate();
        assertThat(status(shipped)).isEqualTo("DELIVERED");
        job.simulate();

        assertThat(status(notShipped)).isEqualTo("ACCEPTED");
        assertThat(outboxTypes(shipped))
                .containsExactly(
                        "ShipmentAccepted",
                        "ShipmentShipped",
                        "ShipmentInTransit",
                        "ShipmentDelivered");
        for (String type : List.of("ShipmentInTransit", "ShipmentDelivered")) {
            assertThat(json.readTree(outboxPayload(shipped, type)).propertyNames())
                    .isEqualTo(
                            json.readTree(Files.readString(EXAMPLES.resolve(type + ".json")))
                                    .propertyNames());
        }
    }

    private String status(UUID sellerOrderId) {
        return jdbc.sql("SELECT status FROM shipment WHERE seller_order_id = :id")
                .param("id", sellerOrderId)
                .query(String.class)
                .single();
    }

    private List<String> outboxTypes(UUID sellerOrderId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_id = :id ORDER BY"
                                + " occurred_at, id")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .list();
    }

    private String outboxPayload(UUID sellerOrderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = :type")
                .param("id", sellerOrderId.toString())
                .param("type", type)
                .query(String.class)
                .single();
    }
}
