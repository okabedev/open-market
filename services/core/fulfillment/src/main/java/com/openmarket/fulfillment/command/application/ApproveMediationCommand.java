package com.openmarket.fulfillment.command.application;

import java.util.UUID;

/**
 * 관리자가 중재 중인 클레임을 승인한다(ADM-2).
 *
 * @param fault 관리자가 고른 귀책
 */
public record ApproveMediationCommand(UUID adminId, UUID claimId, Fault fault) {

    /** 귀책. 상수는 도메인 {@code Fault}와 같다. */
    public enum Fault {
        BUYER,
        SELLER
    }
}
