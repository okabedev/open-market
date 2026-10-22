package com.openmarket.notification.command.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 원인 이벤트 하나로 만들 인앱 알림들.
 *
 * @param sourceEventId 원인 이벤트 ID. 중복 방지 키다
 */
public record NotifyCommand(UUID sourceEventId, List<Notice> notices) {

    /**
     * 알림 하나.
     *
     * @param recipientId 받는 회원. 관리자 알림은 {@code null}
     * @param params 문구에 넣을 값(docs/architecture/notification.md "인앱 알림")
     * @param target 누르면 이동할 대상. 없으면 {@code null}
     */
    public record Notice(
            Kind kind,
            Audience audience,
            @Nullable UUID recipientId,
            Map<String, String> params,
            @Nullable Target target,
            @Nullable UUID targetId) {}

    /** 받는 쪽(도메인 {@code NotificationAudience}와 같은 이름). */
    public enum Audience {
        BUYER,
        SELLER,
        ADMIN
    }

    /** 인앱 알림 종류(도메인 {@code NotificationKind}의 인앱 종류와 같은 이름). */
    public enum Kind {
        ORDER_PAID,
        SHIPMENT_SHIPPED,
        SHIPMENT_DELIVERED,
        CLAIM_APPROVED,
        CLAIM_REJECTED,
        CLAIM_COMPLETED,
        SELLER_REJECTED,
        SELLER_ORDER_RECEIVED,
        CLAIM_REQUESTED,
        SELLER_APPROVED,
        PRODUCT_BANNED,
        CLAIM_MEDIATION_REQUESTED,
        REFUND_FAILED
    }

    /** 이동 대상 종류(도메인 {@code NotificationTarget}과 같은 이름). */
    public enum Target {
        ORDER,
        SELLER_ORDER,
        CLAIM,
        REFUND,
        PRODUCT,
        SELLER_APPLICATION
    }
}
