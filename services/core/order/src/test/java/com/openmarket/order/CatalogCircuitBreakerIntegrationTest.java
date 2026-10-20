package com.openmarket.order;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.openmarket.order.CatalogStub.SERVER;
import static com.openmarket.order.CatalogStub.sku;
import static com.openmarket.order.OrderTokens.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * catalog 동기 호출의 장애 격리(NFR-4): 타임아웃, 5xx, 서킷 열림은 503 {@code ORDER_CATALOG_UNAVAILABLE}이고, 수량 바꾸기와
 * 빼기는 영향이 없다. 값은 테스트 설정(= config-repo order.yml)이다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class CatalogCircuitBreakerIntegrationTest {

    /** 서킷이 판단을 시작하는 최소 호출 수(minimum-number-of-calls). */
    static final int MINIMUM_CALLS = 10;

    @DynamicPropertySource
    static void catalog(DynamicPropertyRegistry registry) {
        CatalogStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired CircuitBreakerRegistry circuitBreakers;

    UUID memberId;
    UUID shirt;

    @BeforeEach
    void givenCartWithOneItem() throws Exception {
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        CatalogStub.reset();
        memberId = UUID.randomUUID();
        shirt = UUID.randomUUID();
        CatalogStub.skus(List.of(sku(shirt, UUID.randomUUID(), "길동상회", "ON_SALE", 19_900, 5)));
        mockMvc.perform(
                        post("/v1/cart/items")
                                .with(buyer(memberId))
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"skuId\":\"" + shirt + "\",\"quantity\":1}"))
                .andExpect(status().isNoContent());
        SERVER.resetRequests();
    }

    @Test
    @DisplayName("catalog가 5xx면 503이고, 실패가 쌓이면 서킷이 열려 catalog를 부르지 않고 바로 503이다")
    void opensOnServerErrors() throws Exception {
        CatalogStub.skusFail(500);

        for (int i = 0; i < MINIMUM_CALLS; i++) {
            cart().andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ORDER_CATALOG_UNAVAILABLE"));
        }
        int calls = catalogCalls();
        assertThat(calls).isEqualTo(MINIMUM_CALLS);

        cart().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORDER_CATALOG_UNAVAILABLE"));
        assertThat(catalogCalls()).isEqualTo(calls);
    }

    @Test
    @DisplayName("서킷이 열려도 수량 바꾸기와 빼기는 된다")
    void changeAndRemoveIgnoreCircuit() throws Exception {
        CatalogStub.skusFail(500);
        for (int i = 0; i <= MINIMUM_CALLS; i++) {
            cart().andExpect(status().isServiceUnavailable());
        }

        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/v1/cart/items/{skuId}", shirt)
                                .with(buyer(memberId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":3}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                        "/v1/cart/items/{skuId}", shirt)
                                .with(buyer(memberId)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("4xx는 order의 잘못이라 500이고 서킷 실패로 세지 않는다")
    void clientErrorsDoNotOpen() throws Exception {
        CatalogStub.skusFail(400);

        for (int i = 0; i <= MINIMUM_CALLS; i++) {
            cart().andExpect(status().isInternalServerError());
        }
        assertThat(catalogCalls()).isEqualTo(MINIMUM_CALLS + 1);
    }

    @Test
    @DisplayName("읽기 타임아웃(2초)을 넘기면 다시 시도하지 않고 503이다")
    void readTimeout() throws Exception {
        SERVER.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(
                                urlPathEqualTo(CatalogStub.SKUS))
                        .willReturn(aResponse().withStatus(200).withFixedDelay(5_000)));

        long started = System.nanoTime();
        cart().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ORDER_CATALOG_UNAVAILABLE"));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    private org.springframework.test.web.servlet.ResultActions cart() throws Exception {
        return mockMvc.perform(get("/v1/cart").with(buyer(memberId)));
    }

    private int catalogCalls() {
        return SERVER.findAll(getRequestedFor(urlPathEqualTo(CatalogStub.SKUS))).size();
    }
}
