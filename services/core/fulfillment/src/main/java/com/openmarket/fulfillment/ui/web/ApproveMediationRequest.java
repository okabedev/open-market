package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.ApproveMediationCommand;
import jakarta.validation.constraints.NotNull;

/**
 * 중재 승인.
 *
 * @param fault 관리자가 고른 귀책. 반품 환불 금액(반품 배송비 차감)을 정한다. 교환은 {@code SELLER}만
 */
public record ApproveMediationRequest(@NotNull ApproveMediationCommand.Fault fault) {}
