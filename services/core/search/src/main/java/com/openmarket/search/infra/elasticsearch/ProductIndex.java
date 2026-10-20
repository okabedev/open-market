package com.openmarket.search.infra.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 상품 인덱스(docs/architecture/search.md "인덱스"). 읽기·쓰기는 별칭 {@link #ALIAS}로 한다. 뜰 때 별칭이 없으면 매핑 파일로 인덱스를
 * 만들고 별칭을 붙인다. Kafka 리스너는 컨텍스트가 다 뜬 뒤 시작하므로 그 전에 인덱스가 있다.
 */
@Component
public class ProductIndex implements InitializingBean {

    /** 읽기·쓰기에 쓰는 별칭. */
    public static final String ALIAS = "products";

    /** 지금 매핑의 인덱스. 매핑을 바꾸면 새 이름으로 만들고 재색인한 뒤 별칭을 옮긴다. */
    static final String INDEX = "products-v1";

    static final String MAPPING = "/elasticsearch/products-v1.json";

    private static final Logger log = LoggerFactory.getLogger(ProductIndex.class);

    private final ElasticsearchClient client;

    public ProductIndex(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (client.indices().existsAlias(a -> a.name(ALIAS)).value()) {
            return;
        }
        try (InputStream mapping = ProductIndex.class.getResourceAsStream(MAPPING)) {
            if (mapping == null) {
                throw new IllegalStateException(MAPPING + "가 없다");
            }
            client.indices().create(c -> c.index(INDEX).withJson(mapping).aliases(ALIAS, a -> a));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        log.info("상품 인덱스 {}와 별칭 {}를 만들었다", INDEX, ALIAS);
    }
}
