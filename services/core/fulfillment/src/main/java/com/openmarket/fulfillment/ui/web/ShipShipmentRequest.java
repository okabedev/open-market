package com.openmarket.fulfillment.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 발송 요청. 규칙(택배사 코드, 송장 번호 형식)은 도메인이 검사한다.
 *
 * @param carrier 택배사 코드. 예: {@code CJ_LOGISTICS}
 * @param trackingNumber 송장 번호. 하이픈 없이 숫자 10~14자리
 */
public record ShipShipmentRequest(@NotNull String carrier, @NotNull String trackingNumber) {}
