package com.openmarket.order;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.openmarket.order.CatalogStub.SERVER;
import static com.openmarket.order.CatalogStub.sku;
import static com.openmarket.order.OrderTokens.buyer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** 장바구니 API(ORD-1). catalog와 auth 토큰 엔드포인트는 WireMock({@link CatalogStub})이다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class CartApiIntegrationTest {

    static final String ITEMS = "/v1/cart/items";

    @DynamicPropertySource
    static void catalog(DynamicPropertyRegistry registry) {
        CatalogStub.register(registry);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JsonMapper json;

    @Autowired JdbcClient jdbc;

    UUID memberId;
    UUID sellerA;
    UUID sellerB;
    UUID shirt;
    UUID pants;
    UUID hat;

    @BeforeEach
    void givenCatalog() {
        CatalogStub.reset();
        memberId = UUID.randomUUID();
        sellerA = UUID.randomUUID();
        sellerB = UUID.randomUUID();
        shirt = UUID.randomUUID();
        pants = UUID.randomUUID();
        hat = UUID.randomUUID();
        CatalogStub.skus(
                List.of(
                        sku(shirt, sellerA, "길동상회", "ON_SALE", 19_900, 5),
                        sku(pants, sellerB, "춘향상점", "ON_SALE", 39_900, 1),
                        sku(hat, sellerA, "길동상회", "ON_SALE", 9_900, 0)));
    }

    @Test
    @DisplayName("담은 SKU를 판매자별로 묶어 보여 준다. 최근에 담은 판매자와 줄이 먼저이고, 줄마다 상태가 나온다")
    void groupsBySeller() throws Exception {
        add(shirt, 2).andExpect(status().isNoContent());
        add(pants, 3).andExpect(status().isNoContent());
        add(hat, 1).andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/cart").with(buyer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellers.length()").value(2))
                .andExpect(jsonPath("$.sellers[0].sellerId").value(sellerA.toString()))
                .andExpect(jsonPath("$.sellers[0].storeName").value("길동상회"))
                .andExpect(jsonPath("$.sellers[0].items[0].skuId").value(hat.toString()))
                .andExpect(jsonPath("$.sellers[0].items[0].status").value("SOLD_OUT"))
                .andExpect(jsonPath("$.sellers[0].items[1].skuId").value(shirt.toString()))
                .andExpect(jsonPath("$.sellers[0].items[1].quantity").value(2))
                .andExpect(jsonPath("$.sellers[0].items[1].price").value(19_900))
                .andExpect(jsonPath("$.sellers[0].items[1].productName").value("반팔 티셔츠"))
                .andExpect(jsonPath("$.sellers[0].items[1].optionValues[0]").value("블랙"))
                .andExpect(jsonPath("$.sellers[0].items[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.sellers[0].items[1].availableQuantity").doesNotExist())
                .andExpect(jsonPath("$.sellers[1].storeName").value("춘향상점"))
                .andExpect(jsonPath("$.sellers[1].items[0].status").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.missingSkuIds.length()").value(0));
    }

    @Test
    @DisplayName("catalog는 서비스 간 토큰으로 부르고, 토큰은 한 번 받아 다시 쓴다")
    void callsCatalogWithServiceToken() throws Exception {
        add(shirt, 1).andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/cart").with(buyer(memberId))).andExpect(status().isOk());

        SERVER.verify(
                getRequestedFor(urlPathEqualTo(CatalogStub.SKUS))
                        .withHeader(
                                "Authorization", equalTo("Bearer " + CatalogStub.ACCESS_TOKEN)));
        assertThat(SERVER.findAll(getRequestedFor(urlPathEqualTo(CatalogStub.SKUS)))).hasSize(2);
        assertThat(SERVER.findAll(postRequestedFor(urlPathEqualTo(CatalogStub.TOKEN))))
                .hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("빈 장바구니는 catalog를 부르지 않고 200이다")
    void emptyCart() throws Exception {
        mockMvc.perform(get("/v1/cart").with(buyer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellers.length()").value(0));

        assertThat(SERVER.findAll(getRequestedFor(urlPathEqualTo(CatalogStub.SKUS)))).isEmpty();
    }

    @Test
    @DisplayName("판매가 멈춘 상품은 담을 수 없고(422), 담아 둔 줄은 NOT_ON_SALE로 보이며 수량 바꾸기와 빼기는 된다")
    void stoppedProduct() throws Exception {
        add(shirt, 1).andExpect(status().isNoContent());
        CatalogStub.skus(List.of(sku(shirt, sellerA, "길동상회", "STOPPED", 19_900, 5)));

        add(shirt, 1)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ORDER_SKU_NOT_ON_SALE"));
        mockMvc.perform(get("/v1/cart").with(buyer(memberId)))
                .andExpect(jsonPath("$.sellers[0].items[0].status").value("NOT_ON_SALE"))
                .andExpect(jsonPath("$.sellers[0].items[0].quantity").value(1));

        mockMvc.perform(
                        put(ITEMS + "/{skuId}", shirt)
                                .with(buyer(memberId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":4}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ITEMS + "/{skuId}", shirt).with(buyer(memberId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ITEMS + "/{skuId}", shirt).with(buyer(memberId)))
                .andExpect(status().isNoContent());
        assertThat(lineCount()).isZero();
    }

    @Test
    @DisplayName("catalog에 없는 SKU는 담을 수 없다(404). 이미 담긴 줄이 catalog에서 사라지면 missingSkuIds로 나온다")
    void unknownSku() throws Exception {
        add(shirt, 1).andExpect(status().isNoContent());
        CatalogStub.skus(List.of());

        add(UUID.randomUUID(), 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_SKU_NOT_FOUND"));
        mockMvc.perform(get("/v1/cart").with(buyer(memberId)))
                .andExpect(jsonPath("$.sellers.length()").value(0))
                .andExpect(jsonPath("$.missingSkuIds[0]").value(shirt.toString()));
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 보내면 수량을 한 번만 더한다. 키가 없으면 400이다")
    void idempotentAdd() throws Exception {
        String key = UUID.randomUUID().toString();
        add(shirt, 2, key).andExpect(status().isNoContent());
        add(shirt, 2, key).andExpect(status().isNoContent());

        assertThat(quantity(shirt)).isEqualTo(2);
        mockMvc.perform(
                        post(ITEMS)
                                .with(buyer(memberId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(shirt, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("수량 규칙: 1~99(400 ORDER_INVALID_INPUT), 더해서 99 초과는 422, 담지 않은 SKU의 수량 바꾸기는 404다")
    void quantityRules() throws Exception {
        add(shirt, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_INVALID_INPUT"));
        add(shirt, 98).andExpect(status().isNoContent());
        add(shirt, 2)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("ORDER_CART_QUANTITY_EXCEEDED"));
        mockMvc.perform(
                        put(ITEMS + "/{skuId}", pants)
                                .with(buyer(memberId))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_CART_ITEM_NOT_FOUND"));
    }

    @Test
    @DisplayName("같은 회원이 동시에 담아도 장바구니는 하나이고 수량이 빠지지 않는다")
    void concurrentAdds() throws Exception {
        int requests = 8;
        try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                calls.add(() -> add(shirt, 1).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(204);
            }
        }

        assertThat(quantity(shirt)).isEqualTo(requests);
        assertThat(
                        jdbc.sql("SELECT count(*) FROM cart WHERE member_id = :id")
                                .param("id", memberId)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void requiresToken() throws Exception {
        mockMvc.perform(get("/v1/cart")).andExpect(status().isUnauthorized());
    }

    private ResultActions add(UUID skuId, int quantity) throws Exception {
        return add(skuId, quantity, UUID.randomUUID().toString());
    }

    private ResultActions add(UUID skuId, int quantity, String key) throws Exception {
        return mockMvc.perform(
                post(ITEMS)
                        .with(buyer(memberId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(skuId, quantity)));
    }

    private String body(UUID skuId, int quantity) {
        return json.writeValueAsString(Map.of("skuId", skuId.toString(), "quantity", quantity));
    }

    private int quantity(UUID skuId) {
        return jdbc.sql(
                        "SELECT quantity FROM cart_item WHERE member_id = :member AND sku_id = :sku")
                .param("member", memberId)
                .param("sku", skuId)
                .query(Integer.class)
                .single();
    }

    private int lineCount() {
        return jdbc.sql("SELECT count(*) FROM cart_item WHERE member_id = :member")
                .param("member", memberId)
                .query(Integer.class)
                .single();
    }
}
