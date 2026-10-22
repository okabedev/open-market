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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** 중재 요청 → 관리자 목록 → 승인 또는 거부 확정(docs/architecture/fulfillment.md "중재"). */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "openmarket.fulfillment.simulator.in-transit-after=0s",
            "openmarket.fulfillment.simulator.deliver-after=0s",
            "openmarket.fulfillment.simulator.interval=1h",
            "openmarket.fulfillment.purchase-confirm.interval=1h"
        })
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class MediationIntegrationTest {

    static final Path EXAMPLES = Path.of("src/test/resources/events");

    @Autowired MockMvc mockMvc;

    @Autowired CreateShipmentsUseCase create;

    @Autowired AcceptShipmentUseCase accept;

    @Autowired ShipShipmentUseCase ship;

    @Autowired ShipmentSimulatorJob simulator;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    final UUID sellerId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("거부된 반품의 중재를 요청하면 관리자 목록에 나온다. 관리자가 판매자 귀책으로 승인하면 전액 환불·재입고 없음으로 승인되고 감사 로그가 남는다")
    void approves() throws Exception {
        CreateShipmentsCommand command = delivered();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        UUID claimId = rejectedClaim(command);

        mediation(claimId, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_CLAIM_NOT_FOUND"));
        mediation(claimId, command.buyerId()).andExpect(status().isNoContent());
        mediation(claimId, command.buyerId()).andExpect(status().isNoContent());
        assertThat(outboxTypes(sellerOrderId)).containsOnlyOnce("ClaimMediationRequested");
        assertSameFields(sellerOrderId, "ClaimMediationRequested");

        mockMvc.perform(get("/v1/admin/claims").with(FulfillmentTokens.buyer(command.buyerId())))
                .andExpect(status().isForbidden());
        String list =
                mockMvc.perform(
                                get("/v1/admin/claims?size=100")
                                        .with(FulfillmentTokens.admin(adminId)))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var item =
                json.readTree(list)
                        .get("items")
                        .valueStream()
                        .filter(
                                node ->
                                        node.get("claim")
                                                .get("claimId")
                                                .asString()
                                                .equals(claimId.toString()))
                        .findFirst()
                        .orElseThrow();
        assertThat(item.get("sellerId").asString()).isEqualTo(sellerId.toString());
        assertThat(item.get("amount").asLong()).isEqualTo(19_900);
        assertThat(item.get("claim").get("status").asString()).isEqualTo("MEDIATING");
        assertThat(item.get("claim").get("rejectionReason").asString()).isEqualTo("사용 흔적");

        decide(claimId, "approve", "{\"fault\":\"SELLER\"}").andExpect(status().isNoContent());
        decide(claimId, "approve", "{\"fault\":\"SELLER\"}").andExpect(status().isNoContent());
        decide(claimId, "reject", "{\"reason\":\"늦은 거부\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.claimStatus").value("APPROVED"));

        String approved = payload(sellerOrderId, "ClaimApproved");
        assertThat(json.readTree(approved).get("fault").asString()).isEqualTo("SELLER");
        assertThat(json.readTree(approved).get("refundAmount").asLong()).isEqualTo(19_900);
        assertThat(
                        jdbc.sql("SELECT restock FROM claim WHERE id = :id")
                                .param("id", claimId)
                                .query(Boolean.class)
                                .single())
                .isFalse();
        assertThat(audit(claimId)).containsExactly("CLAIM_MEDIATION_APPROVED " + adminId);
    }

    @Test
    @DisplayName("관리자가 거부를 확정하면 ClaimRejected(mediated=true)를 내고, 다시 중재를 요청할 수 없다")
    void rejects() throws Exception {
        CreateShipmentsCommand command = delivered();
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        UUID claimId = rejectedClaim(command);
        decide(claimId, "reject", "{\"reason\":\"x\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.claimStatus").value("REJECTED"));
        mediation(claimId, command.buyerId()).andExpect(status().isNoContent());

        decide(claimId, "reject", "{\"reason\":\"판매자 판단이 맞습니다\"}")
                .andExpect(status().isNoContent());
        decide(claimId, "reject", "{\"reason\":\"판매자 판단이 맞습니다\"}")
                .andExpect(status().isNoContent());

        List<String> rejected = payloads(sellerOrderId, "ClaimRejected");
        assertThat(rejected).hasSize(2);
        assertThat(json.readTree(rejected.getLast()).get("mediated").asBoolean()).isTrue();
        assertThat(json.readTree(rejected.getLast()).get("reason").asString())
                .isEqualTo("판매자 판단이 맞습니다");
        mediation(claimId, command.buyerId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FULFILLMENT_CLAIM_INVALID_STATE"));
        mockMvc.perform(
                        get("/v1/seller-orders/{id}", sellerOrderId)
                                .with(FulfillmentTokens.buyer(command.buyerId())))
                .andExpect(jsonPath("$.claims[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.claims[0].rejectionReason").value("사용 흔적"))
                .andExpect(jsonPath("$.claims[0].mediationReason").value("판매자 판단이 맞습니다"))
                .andExpect(jsonPath("$.claims[0].mediationRequestedAt").isNotEmpty());
        assertThat(audit(claimId)).containsExactly("CLAIM_MEDIATION_REJECTED " + adminId);
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

    /** 구매자 귀책 반품을 신청하고 판매자가 거부한다. */
    private UUID rejectedClaim(CreateShipmentsCommand command) throws Exception {
        UUID sellerOrderId = command.sellerOrders().getFirst().sellerOrderId();
        String created =
                mockMvc.perform(
                                post("/v1/seller-orders/{id}/claims", sellerOrderId)
                                        .with(FulfillmentTokens.buyer(command.buyerId()))
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType("application/json")
                                        .content(
                                                "{\"type\":\"RETURN\",\"reason\":\"CHANGE_OF_MIND\",\"description\":\"x\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        UUID claimId = UUID.fromString(json.readTree(created).get("claimId").asString());
        mockMvc.perform(
                        post("/v1/seller/claims/{id}/reject", claimId)
                                .with(FulfillmentTokens.seller(sellerId))
                                .contentType("application/json")
                                .content("{\"reason\":\"사용 흔적\"}"))
                .andExpect(status().isNoContent());
        return claimId;
    }

    private ResultActions mediation(UUID claimId, UUID buyerId) throws Exception {
        return mockMvc.perform(
                post("/v1/claims/{id}/mediation", claimId).with(FulfillmentTokens.buyer(buyerId)));
    }

    private ResultActions decide(UUID claimId, String action, String body) throws Exception {
        return mockMvc.perform(
                post("/v1/admin/claims/{id}/" + action, claimId)
                        .with(FulfillmentTokens.admin(adminId))
                        .contentType("application/json")
                        .content(body));
    }

    private List<String> outboxTypes(UUID sellerOrderId) {
        return jdbc.sql(
                        "SELECT type FROM outbox_event WHERE aggregate_type = 'fulfillment.claim'"
                                + " AND aggregate_id = :id")
                .param("id", sellerOrderId.toString())
                .query(String.class)
                .list();
    }

    private List<String> payloads(UUID sellerOrderId, String type) {
        return jdbc.sql(
                        "SELECT payload::text FROM outbox_event WHERE aggregate_id = :id AND type"
                                + " = :type ORDER BY occurred_at")
                .param("id", sellerOrderId.toString())
                .param("type", type)
                .query(String.class)
                .list();
    }

    private String payload(UUID sellerOrderId, String type) {
        return payloads(sellerOrderId, type).getLast();
    }

    private List<String> audit(UUID claimId) {
        return jdbc.sql(
                        "SELECT action || ' ' || actor_id FROM audit_log WHERE target_type ="
                                + " 'CLAIM' AND target_id = :id")
                .param("id", claimId.toString())
                .query(String.class)
                .list();
    }

    private void assertSameFields(UUID sellerOrderId, String type) throws Exception {
        assertThat(json.readTree(payload(sellerOrderId, type)).propertyNames())
                .isEqualTo(
                        json.readTree(Files.readString(EXAMPLES.resolve(type + ".json")))
                                .propertyNames());
    }
}
