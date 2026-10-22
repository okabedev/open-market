package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/**
 * 관리자가 중재 중인 클레임의 거부를 확정한다(ADM-2).
 *
 * @param reason 거부 확정 사유. 앞뒤 공백을 지우고 1~500자
 */
public record RejectMediationCommand(UUID adminId, UUID claimId, String reason) {}
