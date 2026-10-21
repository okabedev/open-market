package com.openmarket.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.payment.command.application.PreparePaymentCommand;
import com.openmarket.payment.command.application.PreparePaymentUseCase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 구매자 결제 조회(docs/architecture/payment.md "조회"). 화면은 주문이 결제 대기가 된 뒤 이 API로 결제가 준비됐는지 보고 결제위젯을 그린다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class PaymentQueryApiIntegrationTest {

    @DynamicPropertySource
    static void toss(DynamicPropertyRegistry registry) {
        TossStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired PreparePaymentUseCase prepare;

    @Test
    @DisplayName("본인 주문의 결제 상태와 금액을 준다")
    void readyPayment() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        prepare.execute(new PreparePaymentCommand(orderId, buyerId, 59_700, "반팔 티셔츠"));

        mockMvc.perform(
                        get("/v1/payments/orders/{orderId}", orderId)
                                .with(PaymentTokens.buyer(buyerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.amount").value(59_700));
    }

    @Test
    @DisplayName("결제가 아직 준비되지 않았으면 404 PAYMENT_NOT_FOUND")
    void notPreparedYet() throws Exception {
        mockMvc.perform(
                        get("/v1/payments/orders/{orderId}", UUID.randomUUID())
                                .with(PaymentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("다른 회원의 결제는 없는 결제처럼 404")
    void otherBuyer() throws Exception {
        UUID orderId = UUID.randomUUID();
        prepare.execute(new PreparePaymentCommand(orderId, UUID.randomUUID(), 59_700, "반팔 티셔츠"));

        mockMvc.perform(
                        get("/v1/payments/orders/{orderId}", orderId)
                                .with(PaymentTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/v1/payments/orders/{orderId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
