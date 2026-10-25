package com.openmarket.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.search.command.application.AddProductSalesCommand;
import com.openmarket.search.command.application.AddProductSalesUseCase;
import com.openmarket.search.command.application.IndexProductSnapshotCommand;
import com.openmarket.search.command.application.IndexProductSnapshotUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * 자동완성(docs/architecture/search.md "자동완성 API"). 자동완성은 카테고리로 나눌 수 없으므로 테스트마다 다른 클래스의 상품명과 겹치지 않는 단어를
 * 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ElasticsearchContainerConfiguration.class,
    SearchIntegrationTest.Topics.class
})
class SearchSuggestionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper json;
    @Autowired ElasticsearchClient elasticsearch;
    @Autowired IndexProductSnapshotUseCase indexSnapshot;
    @Autowired AddProductSalesUseCase addSales;

    @Test
    @DisplayName("검색어로 시작하는 단어가 든 판매 중 상품명을 판매량 순으로 주고, 같은 상품명은 하나만 준다")
    void suggestsByWordPrefixInSalesOrder() throws Exception {
        UUID boots = index("방수 등산화", "ON_SALE");
        UUID bag = index("등산 가방 30L", "ON_SALE");
        UUID sameBag = index("등산 가방 30L", "ON_SALE");
        UUID stick = index("접이식 등산 스틱", "ON_SALE");
        index("등산 모자", "STOPPED");
        index("등산 장갑", "BANNED");
        sell(stick, 5);
        sell(sameBag, 3);
        sell(bag, 1);
        sell(boots, 2);
        refresh();

        assertThat(suggest("등")).containsExactly("접이식 등산 스틱", "등산 가방 30L", "방수 등산화");
        assertThat(suggest("등산")).containsExactly("접이식 등산 스틱", "등산 가방 30L", "방수 등산화");
        // 두 번째 단어로도 맞고, 단어마다 앞에서부터 맞춘다
        assertThat(suggest("가")).containsExactly("등산 가방 30L");
        assertThat(suggest("방")).containsExactly("방수 등산화");
        // 검색어의 모든 단어가 있어야 한다
        assertThat(suggest("등산 가")).containsExactly("등산 가방 30L");
        assertThat(suggest("  등산 스  ")).containsExactly("접이식 등산 스틱");
        assertThat(suggest("등산 우산")).isEmpty();
    }

    @Test
    @DisplayName("최대 10개를 준다")
    void returnsAtMostTen() throws Exception {
        for (int i = 1; i <= 12; i++) {
            UUID chair = index("캠핑 의자 " + (char) ('A' + i - 1), "ON_SALE");
            sell(chair, i);
        }
        refresh();

        List<String> names = suggest("캠핑");
        assertThat(names).hasSize(10);
        assertThat(names.getFirst()).isEqualTo("캠핑 의자 L");
        assertThat(names.getLast()).isEqualTo("캠핑 의자 C");
    }

    @Test
    @DisplayName("검색어는 1~50자다. 공백뿐이면 빈 목록이다")
    void validatesQuery() throws Exception {
        mockMvc.perform(get("/v1/suggestions")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/suggestions").param("q", "")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/suggestions").param("q", "가".repeat(51)))
                .andExpect(status().isBadRequest());
        assertThat(suggest("   ")).isEmpty();
        assertThat(suggest("가".repeat(50))).isEmpty();
    }

    private UUID index(String name, String status) {
        UUID productId = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        indexSnapshot.execute(
                new IndexProductSnapshotCommand(
                        productId,
                        2,
                        status,
                        UUID.randomUUID(),
                        "길동상회",
                        category,
                        List.of(category),
                        name,
                        name + " 상품입니다.",
                        List.of(UUID.randomUUID()),
                        19_900,
                        true,
                        1,
                        Instant.parse("2026-10-20T00:00:00Z")));
        return productId;
    }

    private void sell(UUID productId, int quantity) {
        addSales.execute(
                new AddProductSalesCommand(
                        UUID.randomUUID(),
                        List.of(new AddProductSalesCommand.Line(productId, quantity))));
    }

    private void refresh() throws Exception {
        elasticsearch.indices().refresh(r -> r.index("products"));
    }

    private List<String> suggest(String q) throws Exception {
        String body =
                mockMvc.perform(get("/v1/suggestions").param("q", q))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
        List<String> names = new ArrayList<>();
        json.readTree(body).get("items").forEach(item -> names.add(item.get("name").asString()));
        return names;
    }
}
