package com.openmarket.fulfillment.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 구매자가 배송 완료된 하위 주문의 반품·교환을 신청한다(CLM-2).
 *
 * @param description 설명. 앞뒤 공백을 지우고 1~500자
 * @param fileIds 클레임 사진 0~5장
 */
public record RequestClaimCommand(
        UUID buyerId,
        UUID sellerOrderId,
        Type type,
        Reason reason,
        String description,
        List<UUID> fileIds) {

    /** 신청할 수 있는 유형. 상수는 도메인 {@code ClaimType}의 같은 이름이다. 발송 전 취소는 따로 한다. */
    public enum Type {
        RETURN,
        EXCHANGE
    }

    /** 신청할 수 있는 사유. 상수는 도메인 {@code ClaimReason}의 같은 이름이다. */
    public enum Reason {
        CHANGE_OF_MIND,
        DEFECTIVE,
        WRONG_ITEM,
        NOT_AS_DESCRIBED
    }
}
