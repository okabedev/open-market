package com.openmarket.search.infra.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 상품 인덱스(docs/architecture/search.md "인덱스", "인덱스 v2"). 읽기·쓰기는 별칭 {@link #ALIAS}로 한다. 뜰 때 별칭을 지금 매핑의
 * 인덱스({@link #INDEX})에 맞춘다: 없으면 만들고, 이전 인덱스({@link #PREVIOUS})를 가리키면 재색인한 뒤 옮긴다. Kafka 리스너는 컨텍스트가 다
 * 뜬 뒤 시작하므로 그동안 쓰기가 없다.
 */
@Component
public class ProductIndex implements InitializingBean {

    /** 읽기·쓰기에 쓰는 별칭. */
    public static final String ALIAS = "products";

    /** 지금 매핑의 인덱스. 매핑을 바꾸면 새 이름으로 만들고 {@link #PREVIOUS}를 바꾼다. */
    static final String INDEX = "products-v2";

    /** 바로 전 매핑의 인덱스. 별칭이 여기를 가리키면 재색인한다. 되돌릴 때를 위해 지우지 않는다. */
    static final String PREVIOUS = "products-v1";

    static final String MAPPING = "/elasticsearch/products-v2.json";

    private final ElasticsearchClient client;

    public ProductIndex(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        new ProductIndexMigration(client).ensure(ALIAS, PREVIOUS, INDEX, MAPPING);
    }
}
