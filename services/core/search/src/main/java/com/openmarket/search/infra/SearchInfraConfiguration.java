package com.openmarket.search.infra;

import com.openmarket.search.command.domain.SearchProductRepository;
import com.openmarket.search.command.domain.SearchProductWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** search의 빈: 검색 상품 쓰기 도메인 서비스. */
@Configuration(proxyBeanMethods = false)
public class SearchInfraConfiguration {

    @Bean
    SearchProductWriter searchProductWriter(SearchProductRepository products) {
        return new SearchProductWriter(products);
    }
}
