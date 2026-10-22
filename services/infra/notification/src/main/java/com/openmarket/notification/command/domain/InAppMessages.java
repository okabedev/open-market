package com.openmarket.notification.command.domain;

import java.util.Map;
import java.util.Objects;

/**
 * 인앱 알림 문구(docs/architecture/notification.md "인앱 알림"). 알림을 만들 때 정해 저장하고 앱은 그대로 보인다. 개인정보는 넣지 않고,
 * 관리자가 쓴 사유는 200자까지만 넣는다.
 */
public final class InAppMessages {

    static final int MAX_REASON_LENGTH = 200;

    private InAppMessages() {}

    public static String compose(NotificationKind kind, Map<String, String> params) {
        return switch (kind) {
            case ORDER_PAID ->
                    "주문이 완료됐습니다. %s, %s".formatted(get(params, "orderName"), won(params, "amount"));
            case SHIPMENT_SHIPPED -> "주문하신 상품이 발송됐습니다. 송장 번호 " + get(params, "trackingNumber");
            case SHIPMENT_DELIVERED -> "주문하신 상품의 배송이 완료됐습니다.";
            case CLAIM_APPROVED ->
                    "EXCHANGE".equals(get(params, "type"))
                            ? "교환 신청이 승인됐습니다."
                            : "반품 신청이 승인됐습니다. 환불 예정 금액은 %s입니다."
                                    .formatted(won(params, "refundAmount"));
            case CLAIM_REJECTED ->
                    Boolean.parseBoolean(get(params, "mediated"))
                            ? "중재 결과 %s 신청이 거부됐습니다.".formatted(claimType(params))
                            : "%s 신청이 거부됐습니다. 거부 사유를 확인하고 중재를 요청할 수 있습니다."
                                    .formatted(claimType(params));
            case CLAIM_COMPLETED ->
                    "EXCHANGE".equals(get(params, "type"))
                            ? "교환 상품이 발송됐습니다."
                            : "%s 환불이 완료됐습니다. %s"
                                    .formatted(claimType(params), won(params, "refundAmount"));
            case SELLER_REJECTED -> "입점 신청이 반려됐습니다. 사유: " + reason(params);
            case SELLER_ORDER_RECEIVED -> sellerOrderReceived(params);
            case CLAIM_REQUESTED ->
                    "CANCEL".equals(get(params, "type"))
                            ? "구매자가 발송 전 주문을 취소했습니다."
                            : "%s 신청이 들어왔습니다. 승인하거나 거부해 주세요.".formatted(claimType(params));
            case SELLER_APPROVED ->
                    "입점 신청이 승인됐습니다. %s의 상품을 등록하고 판매를 시작할 수 있습니다."
                            .formatted(get(params, "storeName"));
            case PRODUCT_BANNED -> "상품이 제재로 판매 중지됐습니다. 사유: " + reason(params);
            case CLAIM_MEDIATION_REQUESTED -> "%s 클레임에 중재 요청이 들어왔습니다.".formatted(claimType(params));
            case REFUND_FAILED ->
                    "환불이 실패했습니다(%s). 환불 재시도 화면에서 확인해 주세요.".formatted(get(params, "code"));
            case EMAIL_VERIFICATION -> throw new IllegalArgumentException("인앱 알림 종류가 아니다: " + kind);
        };
    }

    private static String sellerOrderReceived(Map<String, String> params) {
        int lineCount = Integer.parseInt(get(params, "lineCount"));
        String products =
                lineCount > 1
                        ? "%s 외 %d건".formatted(get(params, "productName"), lineCount - 1)
                        : get(params, "productName");
        return "새 주문이 들어왔습니다. %s, %s. 발주를 확인해 주세요.".formatted(products, won(params, "amount"));
    }

    private static String claimType(Map<String, String> params) {
        return switch (get(params, "type")) {
            case "CANCEL" -> "취소";
            case "RETURN" -> "반품";
            case "EXCHANGE" -> "교환";
            default -> "클레임";
        };
    }

    private static String won(Map<String, String> params, String name) {
        return "%,d원".formatted(Long.parseLong(get(params, name)));
    }

    private static String reason(Map<String, String> params) {
        String reason = get(params, "reason");
        return reason.length() > MAX_REASON_LENGTH
                ? reason.substring(0, MAX_REASON_LENGTH) + "…"
                : reason;
    }

    private static String get(Map<String, String> params, String name) {
        return Objects.requireNonNull(params.get(name), name);
    }
}
