package com.openmarket.search.query.dao;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.search.query.dto.ProductSearchItemView;
import com.openmarket.search.query.dto.ProductSearchResultView;
import com.openmarket.search.query.dto.ProductSearchSort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 구매자 상품 검색(docs/architecture/search.md "검색 API"). 판매 중인 상품만 찾고, 품절 상품은 어떤 정렬에서도 뒤로 보낸다. 커서는 마지막
 * 결과의 정렬 값이다({@code search_after}).
 */
@Repository
public class ProductSearchQueryDao {

    /** 상품 인덱스 별칭. search 인덱스 설정(infra)과 같은 값이다. */
    static final String INDEX = "products";

    private static final String ON_SALE = "ON_SALE";

    private final ElasticsearchClient client;

    public ProductSearchQueryDao(ElasticsearchClient client) {
        this.client = client;
    }

    public ProductSearchResultView search(Condition condition) {
        List<FieldValue> after =
                condition.cursor() == null ? List.of() : Cursor.decode(condition.cursor());
        SearchResponse<ItemSource> response;
        try {
            response =
                    client.search(
                            s -> {
                                s.index(INDEX)
                                        .query(q -> q.bool(query(condition)))
                                        .sort(sort(condition))
                                        // 전체 수는 Elasticsearch 기본값대로 1만 건까지만 센다. 그 이상을 정확히 세면
                                        // 결과가 많을 때 모든 문서를 훑는다.
                                        .size(condition.size() + 1);
                                if (!after.isEmpty()) {
                                    s.searchAfter(after);
                                }
                                return s;
                            },
                            ItemSource.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Hit<ItemSource>> hits = response.hits().hits();
        boolean hasNext = hits.size() > condition.size();
        List<Hit<ItemSource>> page = hasNext ? hits.subList(0, condition.size()) : hits;
        List<ProductSearchItemView> items = new ArrayList<>(page.size());
        for (Hit<ItemSource> hit : page) {
            ItemSource source = hit.source();
            if (source != null) {
                items.add(source.toView());
            }
        }
        String nextCursor = hasNext ? Cursor.encode(page.getLast().sort()) : null;
        long total =
                response.hits().total() == null ? items.size() : response.hits().total().value();
        return new ProductSearchResultView(items, total, nextCursor);
    }

    private static BoolQuery query(Condition condition) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        bool.filter(f -> f.term(t -> t.field("status").value(ON_SALE)));
        UUID categoryId = condition.categoryId();
        if (categoryId != null) {
            bool.filter(f -> f.term(t -> t.field("categoryPath").value(categoryId.toString())));
        }
        Integer minRating = condition.minRating();
        if (minRating != null) {
            bool.filter(
                    f ->
                            f.range(
                                    r ->
                                            r.number(
                                                    n ->
                                                            n.field("ratingAverage")
                                                                    .gte(
                                                                            minRating
                                                                                    .doubleValue()))));
        }
        Long minPrice = condition.minPrice();
        Long maxPrice = condition.maxPrice();
        if (minPrice != null || maxPrice != null) {
            bool.filter(
                    f ->
                            f.range(
                                    r ->
                                            r.number(
                                                    n -> {
                                                        n.field("minPrice");
                                                        if (minPrice != null) {
                                                            n.gte(minPrice.doubleValue());
                                                        }
                                                        if (maxPrice != null) {
                                                            n.lte(maxPrice.doubleValue());
                                                        }
                                                        return n;
                                                    })));
        }
        String keyword = condition.keyword();
        if (keyword != null) {
            bool.must(
                    m ->
                            m.multiMatch(
                                    mm ->
                                            mm.query(keyword)
                                                    .fields("name^3", "storeName", "description")
                                                    .type(TextQueryType.CrossFields)
                                                    .operator(Operator.And)));
        }
        return bool.build();
    }

    /**
     * 품절 뒤로 → 고른 정렬 → productId(같은 값일 때 순서를 고정해 커서가 끊기지 않게 한다). 관련도가 같으면 판매량 많은 순이다. 관련도 점수에 판매량을
     * 더하지는 않는다(docs/architecture/search.md "정렬과 필터").
     */
    private static List<SortOptions> sort(Condition condition) {
        List<SortOptions> sorts = new ArrayList<>();
        sorts.add(field("inStock", SortOrder.Desc));
        ProductSearchSort sort = condition.sort();
        if (sort == ProductSearchSort.RELEVANCE && condition.keyword() == null) {
            sort = ProductSearchSort.LATEST;
        }
        switch (sort) {
            case RELEVANCE -> {
                sorts.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
                sorts.add(field("salesCount", SortOrder.Desc));
                sorts.add(field("publishedAt", SortOrder.Desc));
            }
            case LATEST -> sorts.add(field("publishedAt", SortOrder.Desc));
            case PRICE_ASC -> sorts.add(field("minPrice", SortOrder.Asc));
            case PRICE_DESC -> sorts.add(field("minPrice", SortOrder.Desc));
            case RATING -> {
                sorts.add(field("ratingAverage", SortOrder.Desc));
                sorts.add(field("ratingCount", SortOrder.Desc));
            }
            case SALES -> sorts.add(field("salesCount", SortOrder.Desc));
        }
        sorts.add(field("productId", SortOrder.Asc));
        return sorts;
    }

    private static SortOptions field(String name, SortOrder order) {
        return SortOptions.of(s -> s.field(f -> f.field(name).order(order).missing("_last")));
    }

    /**
     * 검색 조건.
     *
     * @param keyword 앞뒤 공백을 지운 검색어. 없으면 null
     * @param minRating 평균 별점 하한(1~5). 리뷰가 없는 상품은 빠진다
     * @param cursor 이전 쪽의 {@code nextCursor}
     */
    public record Condition(
            @Nullable String keyword,
            @Nullable UUID categoryId,
            @Nullable Long minPrice,
            @Nullable Long maxPrice,
            @Nullable Integer minRating,
            ProductSearchSort sort,
            @Nullable String cursor,
            int size) {}

    /** 검색 결과에 쓰는 문서 필드. 판매 중인 상품만 찾으므로 내용 필드가 있다. */
    record ItemSource(
            String productId,
            String name,
            String storeName,
            long minPrice,
            boolean inStock,
            @Nullable String thumbnailFileId,
            @Nullable Instant publishedAt,
            @Nullable Double ratingAverage,
            @Nullable Long ratingCount) {

        ProductSearchItemView toView() {
            return new ProductSearchItemView(
                    UUID.fromString(productId),
                    name,
                    storeName,
                    minPrice,
                    inStock,
                    thumbnailFileId == null ? null : UUID.fromString(thumbnailFileId),
                    publishedAt,
                    ratingAverage == null
                            ? null
                            : BigDecimal.valueOf(ratingAverage).setScale(1, RoundingMode.HALF_UP),
                    ratingCount == null ? 0 : ratingCount);
        }
    }

    /**
     * 검색 커서. 마지막 결과의 정렬 값({@code search_after})을 {@code <종류>:<값>}으로 적어 줄바꿈으로 잇고 base64url로 감싼다.
     * 클라이언트에는 불투명한 문자열이다. 형식이 틀리면 400 {@code SEARCH_INVALID_CURSOR}.
     */
    static final class Cursor {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
        private static final int MAX_LENGTH = 1_000;

        private Cursor() {}

        static String encode(List<FieldValue> values) {
            List<String> parts = new ArrayList<>(values.size());
            for (FieldValue value : values) {
                parts.add(
                        switch (value._kind()) {
                            case Long -> "l:" + value.longValue();
                            case Double -> "d:" + value.doubleValue();
                            case Boolean -> "b:" + value.booleanValue();
                            case String -> "s:" + value.stringValue();
                            case Null -> "n:";
                            default -> throw new IllegalStateException("정렬 값 종류: " + value._kind());
                        });
            }
            return ENCODER.encodeToString(
                    String.join("\n", parts).getBytes(StandardCharsets.UTF_8));
        }

        static List<FieldValue> decode(String cursor) {
            if (cursor.length() > MAX_LENGTH) {
                throw invalid();
            }
            String text;
            try {
                text = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw invalid();
            }
            List<FieldValue> values = new ArrayList<>();
            for (String part : text.split("\n", -1)) {
                if (part.length() < 2 || part.charAt(1) != ':') {
                    throw invalid();
                }
                String raw = part.substring(2);
                try {
                    values.add(
                            switch (part.charAt(0)) {
                                case 'l' -> FieldValue.of(Long.parseLong(raw));
                                case 'd' -> FieldValue.of(Double.parseDouble(raw));
                                case 'b' -> FieldValue.of(Boolean.parseBoolean(raw));
                                case 's' -> FieldValue.of(raw);
                                case 'n' -> FieldValue.NULL;
                                default -> throw invalid();
                            });
                } catch (NumberFormatException e) {
                    throw invalid();
                }
            }
            return values;
        }

        private static BusinessException invalid() {
            return new BusinessException(
                    ErrorType.INVALID, "SEARCH_INVALID_CURSOR", "검색 커서가 올바르지 않습니다.");
        }
    }
}
