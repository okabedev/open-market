package com.openmarket.settlement.query.dao;

import static com.openmarket.settlement.command.domain.QCommissionRate.commissionRate;

import com.openmarket.settlement.query.dto.CommissionRateView;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 수수료율 조회. 소분류 카테고리 수만큼이라 쪽으로 나누지 않는다. */
@Repository
public class CommissionRateQueryDao {

    private final JPAQueryFactory query;

    public CommissionRateQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 요율을 정한 카테고리 전부. 카테고리 ID 순. */
    public List<CommissionRateView> findAll() {
        return query.select(
                        Projections.constructor(
                                CommissionRateView.class,
                                commissionRate.categoryId,
                                commissionRate.rateBp,
                                commissionRate.updatedAt))
                .from(commissionRate)
                .orderBy(commissionRate.categoryId.asc())
                .fetch();
    }
}
