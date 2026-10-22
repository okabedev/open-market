package com.openmarket.payment.query.dao;

import static com.openmarket.payment.command.domain.QPayment.payment;
import static com.openmarket.payment.command.domain.QRefund.refund;

import com.openmarket.payment.query.dto.CancelFailureView;
import com.openmarket.payment.query.dto.FailedRefundView;
import com.openmarket.payment.query.dto.PageView;
import com.openmarket.payment.query.dto.PaymentStatus;
import com.openmarket.payment.query.dto.PaymentView;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
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

    /** 실패한 환불. 오래된 요청 순(refund_failed_idx), 쪽 번호. */
    public PageView<FailedRefundView> findFailedRefunds(int page, int size) {
        Predicate failed = refund.status.stringValue().eq("FAILED");
        List<FailedRefundView> items =
                query.select(
                                Projections.constructor(
                                        FailedRefundView.class,
                                        refund.id,
                                        refund.orderId,
                                        refund.claimId,
                                        refund.sellerOrderId,
                                        refund.amount,
                                        refund.failureCode,
                                        refund.attempt,
                                        refund.requestedAt,
                                        refund.updatedAt))
                        .from(refund)
                        .where(failed)
                        .orderBy(refund.requestedAt.asc(), refund.id.asc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        Long total = query.select(refund.count()).from(refund).where(failed).fetchOne();
        return new PageView<>(items, total == null ? 0 : total, page, size);
    }

    /** 토스가 보상 취소를 거절한 결제. 오래된 취소 요청 순(payment_cancel_failed_idx), 쪽 번호. */
    public PageView<CancelFailureView> findCancelFailures(int page, int size) {
        Predicate rejected =
                payment.status
                        .stringValue()
                        .eq("APPROVED")
                        .and(payment.cancelFailureCode.isNotNull());
        List<CancelFailureView> items =
                query.select(
                                Projections.constructor(
                                        CancelFailureView.class,
                                        payment.id,
                                        payment.orderId,
                                        payment.amount,
                                        payment.cancelFailureCode,
                                        payment.cancelAttempt,
                                        payment.cancelRequestedAt))
                        .from(payment)
                        .where(rejected)
                        .orderBy(payment.cancelRequestedAt.asc(), payment.id.asc())
                        .offset((long) page * size)
                        .limit(size)
                        .fetch();
        Long total = query.select(payment.count()).from(payment).where(rejected).fetchOne();
        return new PageView<>(items, total == null ? 0 : total, page, size);
    }
}
