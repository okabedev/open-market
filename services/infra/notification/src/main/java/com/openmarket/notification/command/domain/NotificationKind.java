package com.openmarket.notification.command.domain;

/**
 * 알림 종류(docs/architecture/notification.md). 이메일 종류는 메일 템플릿(templates/mail/)이 하나 있고, 인앱 종류는 문구를
 * {@code InAppMessages}가 만든다.
 */
public enum NotificationKind {
    /** 가입 이메일 인증(NTF-1). 원인 이벤트는 {@code EmailVerificationRequested} */
    EMAIL_VERIFICATION,
    /** 구매자: 주문 완료. {@code OrderPaid} */
    ORDER_PAID,
    /** 구매자: 발송(교환 재발송 포함). {@code ShipmentShipped} */
    SHIPMENT_SHIPPED,
    /** 구매자: 배송 완료. {@code ShipmentDelivered} */
    SHIPMENT_DELIVERED,
    /** 구매자: 반품·교환 승인. {@code ClaimApproved} */
    CLAIM_APPROVED,
    /** 구매자: 판매자 거부, 중재 거부 확정. {@code ClaimRejected} */
    CLAIM_REJECTED,
    /** 구매자: 환불 완료, 교환 재발송. {@code ClaimCompleted} */
    CLAIM_COMPLETED,
    /** 구매자: 입점 반려. {@code SellerRejected} */
    SELLER_REJECTED,
    /** 판매자: 결제된 새 주문. {@code OrderPaid}의 하위 주문마다 */
    SELLER_ORDER_RECEIVED,
    /** 판매자: 클레임 신청(취소 포함). {@code ClaimRequested} */
    CLAIM_REQUESTED,
    /** 판매자: 입점 승인. {@code SellerApproved} */
    SELLER_APPROVED,
    /** 판매자: 상품 제재. {@code ProductBanned} */
    PRODUCT_BANNED,
    /** 관리자: 중재 요청. {@code ClaimMediationRequested} */
    CLAIM_MEDIATION_REQUESTED,
    /** 관리자: 환불 실패. {@code RefundFailed} */
    REFUND_FAILED
}
