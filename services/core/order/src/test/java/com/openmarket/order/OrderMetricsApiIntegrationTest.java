package com.openmarket.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 주문 지표(ADM-4, docs/architecture/order.md "운영 지표"). 날짜는 KST이고, 주문 수는 주문 시각, 결제된 주문 수와 GMV는 결제
 * 시각으로 센다. 다른 테스트와 겹치지 않게 2025년 3월 날짜를 쓴다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class OrderMetricsApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("날짜(KST)별 주문 수, 결제된 주문 수, GMV를 주고 주문이 없는 날도 0으로 준다. 취소된 주문도 주문 수에 든다")
    void countsOrdersByKstDay() throws Exception {
        // 2025-03-03 KST = 2025-03-02T15:00Z ~ 2025-03-03T15:00Z
        insert(
                "PAID",
                30_000,
                "2025-03-02T15:30:00Z",
                "2025-03-02T15:40:00Z"); // 3일 00:30 KST 주문·결제
        insert("CANCELLED", 10_000, "2025-03-03T05:00:00Z", null); // 3일 주문, 결제 안 됨
        insert(
                "PAID",
                20_000,
                "2025-03-03T14:50:00Z",
                "2025-03-03T15:10:00Z"); // 3일 23:50 주문, 4일 00:10 결제
        insert(
                "PAID",
                99_000,
                "2025-03-02T14:59:00Z",
                "2025-03-02T14:59:30Z"); // 2일 23:59 KST, 기간 밖

        mockMvc.perform(
                        get("/v1/admin/metrics/orders")
                                .param("from", "2025-03-03")
                                .param("to", "2025-03-05")
                                .with(OrderTokens.admin(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-03-03"))
                .andExpect(jsonPath("$.to").value("2025-03-05"))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2025-03-03"))
                .andExpect(jsonPath("$.days[0].orderCount").value(3))
                .andExpect(jsonPath("$.days[0].paidOrderCount").value(1))
                .andExpect(jsonPath("$.days[0].gmv").value(30_000))
                .andExpect(jsonPath("$.days[1].date").value("2025-03-04"))
                .andExpect(jsonPath("$.days[1].orderCount").value(0))
                .andExpect(jsonPath("$.days[1].paidOrderCount").value(1))
                .andExpect(jsonPath("$.days[1].gmv").value(20_000))
                .andExpect(jsonPath("$.days[2].orderCount").value(0))
                .andExpect(jsonPath("$.days[2].paidOrderCount").value(0))
                .andExpect(jsonPath("$.days[2].gmv").value(0));
    }

    @Test
    @DisplayName(
            "기간이 31일을 넘으면 400 ORDER_METRICS_PERIOD_TOO_LONG, 거꾸로면 400 ORDER_INVALID_INPUT, 관리자가 아니면 403")
    void validatesPeriodAndRole() throws Exception {
        mockMvc.perform(
                        get("/v1/admin/metrics/orders")
                                .param("from", "2025-03-01")
                                .param("to", "2025-04-01")
                                .with(OrderTokens.admin(adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_METRICS_PERIOD_TOO_LONG"));
        mockMvc.perform(
                        get("/v1/admin/metrics/orders")
                                .param("from", "2025-03-01")
                                .param("to", "2025-03-31")
                                .with(OrderTokens.admin(adminId)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/v1/admin/metrics/orders")
                                .param("from", "2025-03-05")
                                .param("to", "2025-03-04")
                                .with(OrderTokens.admin(adminId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_INPUT"));
        mockMvc.perform(
                        get("/v1/admin/metrics/orders")
                                .param("from", "2025-03-01")
                                .param("to", "2025-03-02")
                                .with(OrderTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private void insert(String status, long amount, String placedAt, @Nullable String paidAt) {
        jdbc.sql(
                        "INSERT INTO orders (id, buyer_id, status, amount, order_name, recipient_name,"
                                + " recipient_phone, zip_code, base_address, placed_at, paid_at, updated_at,"
                                + " version) VALUES (:id, :buyer, :status, :amount, '지표 주문', '홍길동',"
                                + " '01012345678', '06236', '서울', :placedAt, :paidAt, :placedAt, 0)")
                .param("id", UUID.randomUUID())
                .param("buyer", UUID.randomUUID())
                .param("status", status)
                .param("amount", amount)
                .param("placedAt", Timestamp.from(Instant.parse(placedAt)))
                .param(
                        "paidAt",
                        paidAt == null ? null : Timestamp.from(Instant.parse(paidAt)),
                        java.sql.Types.TIMESTAMP)
                .update();
    }
}
