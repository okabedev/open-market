package com.openmarket.catalog.infra;

import com.openmarket.catalog.command.domain.CategoryRepository;
import com.openmarket.catalog.command.domain.ProductAvailabilityCalculator;
import com.openmarket.catalog.command.domain.ProductAvailabilityRepository;
import com.openmarket.catalog.command.domain.ProductSnapshotFactory;
import com.openmarket.catalog.command.domain.SellerRepository;
import com.openmarket.catalog.command.domain.StockRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** catalog의 빈: 시계, 조회 DAO의 QueryDSL, 상품 스냅샷·판매 가능 여부 도메인 서비스. */
@Configuration(proxyBeanMethods = false)
public class CatalogInfraConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }

    @Bean
    ProductSnapshotFactory productSnapshotFactory(
            CategoryRepository categories,
            SellerRepository sellers,
            StockRepository stocks,
            ProductAvailabilityRepository availabilities) {
        return new ProductSnapshotFactory(categories, sellers, stocks, availabilities);
    }

    @Bean
    ProductAvailabilityCalculator productAvailabilityCalculator(
            StockRepository stocks, ProductAvailabilityRepository availabilities) {
        return new ProductAvailabilityCalculator(stocks, availabilities);
    }
}
