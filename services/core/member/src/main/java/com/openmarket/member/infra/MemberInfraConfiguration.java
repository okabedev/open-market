package com.openmarket.member.infra;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MemberInfraConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** 조회 dao가 쓴다. 트랜잭션에 묶인 EntityManager 프록시를 받는다. */
    @Bean
    JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
