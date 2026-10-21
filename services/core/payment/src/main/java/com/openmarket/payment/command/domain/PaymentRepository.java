package com.openmarket.payment.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 결제 저장소. 구현은 {@code infra}에 있다. */
public interface PaymentRepository {

    Payment save(Payment payment);

    boolean existsByOrderId(UUID orderId);

    /** 결제 행을 잠그고 읽는다(상태 전이). */
    Optional<Payment> findForUpdateById(UUID id);

    /** 결제 행을 잠그고 읽는다(승인 요청). */
    Optional<Payment> findForUpdateByOrderId(UUID orderId);

    /** {@code requestedBefore} 이전에 승인을 요청한 {@code IN_PROGRESS} 결제. 오래된 순, 최대 {@code limit}개. */
    List<UUID> findInProgressIdsRequestedBefore(Instant requestedBefore, int limit);
}
