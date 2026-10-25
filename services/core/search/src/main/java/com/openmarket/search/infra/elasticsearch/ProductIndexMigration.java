package com.openmarket.search.infra.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.ReindexResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 별칭을 지금 매핑의 인덱스에 맞춘다(docs/architecture/search.md "인덱스 v2"). 이름을 인자로 받아 테스트가 다른 이름으로 같은 절차를 확인한다.
 *
 * <ul>
 *   <li>별칭이 없으면 지금 인덱스를 만들고 별칭을 붙인다
 *   <li>별칭이 지금 인덱스를 가리키면 아무것도 하지 않는다
 *   <li>별칭이 이전 인덱스를 가리키면 지금 인덱스를 만들고 {@code _reindex}(새로 고침까지 기다림)로 옮긴 뒤, 별칭 이동을 한 요청(원자)으로 한다. 이전
 *       인덱스는 남긴다
 * </ul>
 *
 * <p>지금 인덱스가 별칭 없이 남아 있으면(전에 옮기다 멈춤) 지우고 다시 만든다. 리스너가 뜨기 전이라 그 인덱스에 쓴 것이 없다. 인스턴스는 하나라는 전제다(로컬).
 */
class ProductIndexMigration {

    private static final Logger log = LoggerFactory.getLogger(ProductIndexMigration.class);

    private final ElasticsearchClient client;

    ProductIndexMigration(ElasticsearchClient client) {
        this.client = client;
    }

    /** 무엇을 했는지 돌려준다. */
    Result ensure(String alias, String previous, String current, String mapping)
            throws IOException {
        boolean aliased = client.indices().existsAlias(a -> a.name(alias)).value();
        Set<String> targets =
                aliased
                        ? client.indices().getAlias(g -> g.name(alias)).aliases().keySet()
                        : Set.of();
        if (targets.contains(current)) {
            return Result.UP_TO_DATE;
        }
        if (client.indices().exists(e -> e.index(current)).value()) {
            client.indices().delete(d -> d.index(current));
            log.warn("별칭 없이 남은 인덱스 {}를 지우고 다시 만든다", current);
        }
        if (!targets.contains(previous)) {
            create(current, mapping, alias);
            log.info("상품 인덱스 {}와 별칭 {}를 만들었다", current, alias);
            return Result.CREATED;
        }
        create(current, mapping, null);
        ReindexResponse reindex =
                client.reindex(
                        r ->
                                r.source(s -> s.index(previous))
                                        .dest(d -> d.index(current))
                                        .refresh(true)
                                        .waitForCompletion(true));
        if (!reindex.failures().isEmpty()) {
            throw new IllegalStateException(
                    previous + " → " + current + " 재색인 실패: " + reindex.failures());
        }
        client.indices()
                .updateAliases(
                        u ->
                                u.actions(a -> a.remove(rm -> rm.index(previous).alias(alias)))
                                        .actions(a -> a.add(ad -> ad.index(current).alias(alias))));
        log.info(
                "상품 인덱스를 {}에서 {}로 옮겼다(문서 {}건). 별칭 {}가 {}를 가리킨다",
                previous,
                current,
                reindex.total(),
                alias,
                current);
        return Result.MIGRATED;
    }

    private void create(String index, String mapping, @Nullable String alias) throws IOException {
        try (InputStream body = ProductIndexMigration.class.getResourceAsStream(mapping)) {
            if (body == null) {
                throw new IllegalStateException(mapping + "가 없다");
            }
            client.indices()
                    .create(
                            c -> {
                                c.index(index).withJson(body);
                                if (alias != null) {
                                    c.aliases(alias, a -> a);
                                }
                                return c;
                            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    enum Result {
        CREATED,
        UP_TO_DATE,
        MIGRATED
    }
}
