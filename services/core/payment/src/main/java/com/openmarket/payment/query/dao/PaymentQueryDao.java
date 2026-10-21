package com.openmarket.payment.query.dao;

import static com.openmarket.payment.command.domain.QPayment.payment;

import com.openmarket.payment.query.dto.PaymentStatus;
import com.openmarket.payment.query.dto.PaymentView;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 결제 조회. 주문당 결제는 하나다(order_id 유일 인덱스). */
@Repository
public class PaymentQueryDao {

    private final JPAQueryFactory query;

    public PaymentQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 구매자 본인 주문의 결제. 결제가 아직 없거나 다른 회원의 것이면 비어 있다. */
    public Optional<PaymentView> findForBuyer(UUID buyerId, UUID orderId) {
        Tuple row =
                query.select(payment.status.stringValue(), payment.amount)
                        .from(payment)
                        .where(payment.orderId.eq(orderId), payment.buyerId.eq(buyerId))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                new PaymentView(
                        orderId,
                        PaymentStatus.valueOf(Objects.requireNonNull(row.get(0, String.class))),
                        Objects.requireNonNull(row.get(payment.amount))));
    }
}
