package com.openmarket.search.query.dao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.openmarket.search.query.dto.SuggestionView;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 자동완성(docs/architecture/search.md "자동완성 API"). {@code name.suggest}(단어마다 앞에서부터 1~20글자를 색인)에서 검색어의
 * 모든 단어를 찾고, 판매량 많은 순으로 상품명을 준다. 같은 상품명은 여기서 걸러 하나만 남긴다.
 */
@Repository
public class SuggestionQueryDao {

    /**
     * 같은 상품명을 거르기 전에 가져오는 후보 수. {@code name}은 text라 Elasticsearch에서 상품명으로 묶을 수 없다({@code
     * collapse}에는 keyword가 필요하다). 후보 안에 같은 이름이 많으면 결과가 최대 개수보다 적을 수 있다.
     */
    static final int CANDIDATES = 50;

    private static final String ON_SALE = "ON_SALE";

    private final ElasticsearchClient client;

    public SuggestionQueryDao(ElasticsearchClient client) {
        this.client = client;
    }

    /**
     * 검색어로 시작하는 단어가 든 판매 중 상품명을 판매량 많은 순으로 찾는다.
     *
     * @param keyword 앞뒤 공백을 지운 검색어
     * @param limit 최대 개수
     */
    public List<SuggestionView> suggest(String keyword, int limit) {
        SearchResponse<NameSource> response;
        try {
            response =
                    client.search(
                            s ->
                                    s.index(ProductSearchQueryDao.INDEX)
                                            .query(q -> q.bool(query(keyword)))
                                            .source(src -> src.filter(f -> f.includes("name")))
                                            .sort(sort())
                                            .trackTotalHits(t -> t.enabled(false))
                                            .size(CANDIDATES),
                            NameSource.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> names = new LinkedHashSet<>();
        for (Hit<NameSource> hit : response.hits().hits()) {
            NameSource source = hit.source();
            if (source != null) {
                names.add(source.name());
            }
            if (names.size() == limit) {
                break;
            }
        }
        List<SuggestionView> items = new ArrayList<>(names.size());
        names.forEach(name -> items.add(new SuggestionView(name)));
        return items;
    }

    private static BoolQuery query(String keyword) {
        return BoolQuery.of(
                b ->
                        b.filter(f -> f.term(t -> t.field("status").value(ON_SALE)))
                                .must(
                                        m ->
                                                m.match(
                                                        mt ->
                                                                mt.field("name.suggest")
                                                                        .query(keyword)
                                                                        .operator(Operator.And))));
    }

    /** 판매량 많은 순 → 관련도 → 판매 시작 최신 순 → productId(순서를 고정한다). */
    private static List<SortOptions> sort() {
        return List.of(
                field("salesCount", SortOrder.Desc),
                SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))),
                field("publishedAt", SortOrder.Desc),
                field("productId", SortOrder.Asc));
    }

    private static SortOptions field(String name, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(name).order(order).missing("_last")));
    }

    record NameSource(String name) {}
}
