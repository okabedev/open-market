package com.openmarket.settlement.infra.persistence;

import com.openmarket.settlement.command.domain.SellerLock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres 트랜잭션 advisory lock으로 판매자를 잠근다({@code pg_advisory_xact_lock}). 잠금은 트랜잭션이 끝나면 풀린다. 키는 판매자
 * ID의 두 64비트 절반을 XOR한 값이다. 다른 판매자와 키가 겹치면 그 둘이 서로 기다릴 뿐 정합성에는 영향이 없다. settlement 스키마에서 advisory
 * lock을 쓰는 곳은 여기뿐이다(ShedLock은 테이블을 쓴다).
 */
@Component
class JdbcSellerLock implements SellerLock {

    private final JdbcClient jdbc;

    JdbcSellerLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID sellerId) {
        long key = sellerId.getMostSignificantBits() ^ sellerId.getLeastSignificantBits();
        jdbc.sql("SELECT pg_advisory_xact_lock(?)").param(key).query(Object.class).single();
    }
}
