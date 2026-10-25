package com.openmarket.search.infra.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.openmarket.common.test.container.KafkaContainerConfiguration;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.search.ElasticsearchContainerConfiguration;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 인덱스 v1 → v2 전환(docs/architecture/search.md "인덱스 v2"). 앱이 쓰는 별칭({@code products})을 건드리지 않게 테스트마다
 * 다른 이름으로 같은 절차를 돌린다.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import({
    PostgresContainerConfiguration.class,
    KafkaContainerConfiguration.class,
    ElasticsearchContainerConfiguration.class
})
class ProductIndexMigrationIntegrationTest {

    @Autowired ElasticsearchClient client;

    @Test
    @DisplayName("별칭이 v1을 가리키면 v2를 만들어 재색인하고 별칭을 옮긴다. v1은 남고, 다시 돌리면 아무것도 하지 않는다")
    void migratesFromV1() throws Exception {
        String name = "migtest-" + UUID.randomUUID().toString().substring(0, 8);
        String alias = name;
        String v1 = name + "-v1";
        String v2 = name + "-v2";
        try (InputStream mapping =
                ProductIndexMigration.class.getResourceAsStream(
                        "/elasticsearch/products-v1.json")) {
            client.indices().create(c -> c.index(v1).withJson(mapping).aliases(alias, a -> a));
        }
        String productId = UUID.randomUUID().toString();
        client.index(
                i ->
                        i.index(alias)
                                .id(productId)
                                .document(
                                        Map.of(
                                                "productId",
                                                productId,
                                                "revision",
                                                2,
                                                "status",
                                                "ON_SALE",
                                                "name",
                                                "반팔 티셔츠",
                                                "inStock",
                                                true,
                                                "availabilityRevision",
                                                1))
                                .refresh(co.elastic.clients.elasticsearch._types.Refresh.True));

        ProductIndexMigration migration = new ProductIndexMigration(client);
        assertThat(migration.ensure(alias, v1, v2, ProductIndex.MAPPING))
                .isEqualTo(ProductIndexMigration.Result.MIGRATED);

        assertThat(client.indices().getAlias(g -> g.name(alias)).aliases()).containsOnlyKeys(v2);
        assertThat(client.indices().exists(e -> e.index(v1)).value()).isTrue();
        GetResponse<Map> moved = client.get(g -> g.index(alias).id(productId), Map.class);
        assertThat(moved.found()).isTrue();
        assertThat(moved.source().get("name")).isEqualTo("반팔 티셔츠");
        // v2 매핑(strict)이 새 필드를 받는다
        client.update(
                u ->
                        u.index(alias)
                                .id(productId)
                                .doc(
                                        Map.of(
                                                "salesCount",
                                                3,
                                                "salesRevision",
                                                1,
                                                "ratingAverage",
                                                4.5)),
                Map.class);

        assertThat(migration.ensure(alias, v1, v2, ProductIndex.MAPPING))
                .isEqualTo(ProductIndexMigration.Result.UP_TO_DATE);
    }

    @Test
    @DisplayName("별칭이 없으면 v2를 만들고 별칭을 붙인다. 별칭 없이 남은 v2는 지우고 다시 만든다")
    void createsWhenMissing() throws Exception {
        String name = "migtest-" + UUID.randomUUID().toString().substring(0, 8);
        String v2 = name + "-v2";
        client.indices().create(c -> c.index(v2));

        assertThat(
                        new ProductIndexMigration(client)
                                .ensure(name, name + "-v1", v2, ProductIndex.MAPPING))
                .isEqualTo(ProductIndexMigration.Result.CREATED);

        assertThat(client.indices().getAlias(g -> g.name(name)).aliases()).containsOnlyKeys(v2);
        assertThat(client.indices().getMapping(m -> m.index(v2)).get(v2).mappings().properties())
                .containsKeys("salesCount", "ratingAverage", "name");
    }
}
