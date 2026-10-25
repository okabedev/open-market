package com.openmarket.notification.command.domain;

/**
 * 알림 종류(docs/architecture/notification.md). 인앱 종류는 문구를 {@code InAppMessages}가 만든다. {@link
 * #emailed()}인 종류는 같은 문구로 이메일도 보낸다(받는 주소는 보낼 때 member에서 조회한다).
 */
public enum NotificationKind {
    /** 가입 이메일 인증(NTF-1). 원인 이벤트는 {@code EmailVerificationRequested}. 이메일만 보낸다 */
    EMAIL_VERIFICATION(false),
    /** 구매자: 주문 완료. {@code OrderPaid} */
    ORDER_PAID(true),
    /** 구매자: 발송(교환 재발송 포함). {@code ShipmentShipped} */
    SHIPMENT_SHIPPED(true),
    /** 구매자: 배송 완료. {@code ShipmentDelivered} */
    SHIPMENT_DELIVERED(false),
    /** 구매자: 반품·교환 승인. {@code ClaimApproved} */
    CLAIM_APPROVED(true),
    /** 구매자: 판매자 거부, 중재 거부 확정. {@code ClaimRejected} */
    CLAIM_REJECTED(true),
    /** 구매자: 환불 완료, 교환 재발송. {@code ClaimCompleted} */
    CLAIM_COMPLETED(true),
    /** 구매자: 입점 반려. {@code SellerRejected} */
    SELLER_REJECTED(true),
    /** 판매자: 결제된 새 주문. {@code OrderPaid}의 하위 주문마다 */
    SELLER_ORDER_RECEIVED(false),
    /** 판매자: 클레임 신청(취소 포함). {@code ClaimRequested} */
    CLAIM_REQUESTED(false),
    /** 판매자: 입점 승인. {@code SellerApproved} */
    SELLER_APPROVED(true),
    /** 판매자: 상품 제재. {@code ProductBanned} */
    PRODUCT_BANNED(true),
    /** 판매자: 새 리뷰. {@code ReviewCreated}. 판매자 화면에서 답하므로 인앱만이다 */
    REVIEW_CREATED(false),
    /** 판매자: 정산 확정(NTF-1). {@code SettlementConfirmed} */
    SETTLEMENT_CONFIRMED(true),
    /** 판매자: 정산 지급. {@code SettlementPaidOut} */
    SETTLEMENT_PAID_OUT(true),
    /** 관리자: 중재 요청. {@code ClaimMediationRequested} */
    CLAIM_MEDIATION_REQUESTED(false),
    /** 관리자: 환불 실패. {@code RefundFailed} */
    REFUND_FAILED(false);

    private final boolean emailed;

    NotificationKind(boolean emailed) {
        this.emailed = emailed;
    }

    /** 인앱 알림과 함께 이메일도 보내는 종류인가. 관리자 알림은 받는 사람이 없어 보내지 않는다. */
    public boolean emailed() {
        return emailed;
    }
}
