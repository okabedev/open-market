package com.openmarket.payment.query.dto;

/** 조회용 결제 상태. 상수는 커맨드 쪽 {@code PaymentStatus}와 같다. */
public enum PaymentStatus {
    READY,
    IN_PROGRESS,
    APPROVED,
    FAILED,
    CANCELLED
}
