package com.openmarket.payment.command.domain;

/** 결제 원장 저장소. 추가만 한다. 구현은 {@code infra}에 있다. */
public interface PaymentLedgerRepository {

    PaymentLedgerEntry save(PaymentLedgerEntry entry);
}
