package com.openmarket.payment.command.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 환불 저장소. 구현은 {@code infra}에 있다. */
public interface RefundRepository {

    Refund save(Refund refund);

    boolean existsByClaimId(UUID claimId);

    /** 환불 행을 잠그고 읽는다(환불 작업의 반영). */
    Optional<Refund> findForUpdateById(UUID id);

    /** 결제의 진행 중({@code REQUESTED}) 환불 금액의 합. */
    long sumRequestedAmountByPaymentId(UUID paymentId);

    /** 토스 부분취소를 부를 {@code REQUESTED} 환불. 오래된 순, 최대 {@code limit}개. */
    List<UUID> findRequestedIds(int limit);
}
