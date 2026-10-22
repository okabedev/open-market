package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/**
 * 클레임의 환불이 실패했다({@code RefundFailed}).
 *
 * @param code 토스 오류 코드, 또는 payment의 {@code PAYMENT_NOT_REFUNDABLE}, {@code REFUND_AMOUNT_EXCEEDED}
 */
public record RecordRefundFailureCommand(UUID claimId, String code) {}
