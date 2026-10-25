package com.openmarket.notification.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InAppMessagesTest {

    @Test
    @DisplayName("금액은 천 단위로 끊어 쓴다")
    void formatsAmount() {
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.ORDER_PAID,
                                Map.of("orderName", "반팔 티셔츠 외 1건", "amount", "59700")))
                .isEqualTo("주문이 완료됐습니다. 반팔 티셔츠 외 1건, 59,700원");
    }

    @Test
    @DisplayName("클레임 문구는 종류(취소·반품·교환)와 결과에 따라 다르다")
    void claimMessages() {
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_REQUESTED, Map.of("type", "CANCEL")))
                .isEqualTo("구매자가 발송 전 주문을 취소했습니다.");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_REQUESTED, Map.of("type", "RETURN")))
                .isEqualTo("반품 신청이 들어왔습니다. 승인하거나 거부해 주세요.");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_APPROVED,
                                Map.of("type", "RETURN", "refundAmount", "13900")))
                .isEqualTo("반품 신청이 승인됐습니다. 환불 예정 금액은 13,900원입니다.");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_APPROVED,
                                Map.of("type", "EXCHANGE", "refundAmount", "0")))
                .isEqualTo("교환 신청이 승인됐습니다.");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_REJECTED,
                                Map.of("type", "RETURN", "mediated", "false")))
                .isEqualTo("반품 신청이 거부됐습니다. 거부 사유를 확인하고 중재를 요청할 수 있습니다.");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_REJECTED,
                                Map.of("type", "EXCHANGE", "mediated", "true")))
                .isEqualTo("중재 결과 교환 신청이 거부됐습니다.");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_COMPLETED,
                                Map.of("type", "CANCEL", "refundAmount", "39800")))
                .isEqualTo("취소 환불이 완료됐습니다. 39,800원");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.CLAIM_COMPLETED,
                                Map.of("type", "EXCHANGE", "refundAmount", "0")))
                .isEqualTo("교환 상품이 발송됐습니다.");
    }

    @Test
    @DisplayName("새 리뷰는 상품명과 별점만 넣는다. 정산은 기간(한국 날짜, 끝 날 포함)과 금액을 넣는다")
    void reviewAndSettlementMessages() {
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.REVIEW_CREATED,
                                Map.of("productName", "반팔 티셔츠", "rating", "5")))
                .isEqualTo("반팔 티셔츠에 새 리뷰가 등록됐습니다. 별점 5점");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.SETTLEMENT_CONFIRMED,
                                Map.of(
                                        "periodStart", "2026-10-25T15:00:00Z",
                                        "periodEnd", "2026-11-01T15:00:00Z",
                                        "amount", "43417")))
                .isEqualTo("10월 26일~11월 1일 판매분 정산이 확정됐습니다. 정산 금액 43,417원");
        assertThat(
                        InAppMessages.compose(
                                NotificationKind.SETTLEMENT_PAID_OUT, Map.of("amount", "43417")))
                .isEqualTo("정산 금액 43,417원이 지급됐습니다.");
    }

    @Test
    @DisplayName("관리자가 쓴 사유는 200자까지만 넣는다")
    void truncatesReason() {
        String message =
                InAppMessages.compose(
                        NotificationKind.PRODUCT_BANNED, Map.of("reason", "가".repeat(300)));

        assertThat(message).isEqualTo("상품이 제재로 판매 중지됐습니다. 사유: " + "가".repeat(200) + "…");
    }

    @Test
    @DisplayName("인앱 종류마다 문구가 있고 500자를 넘지 않는다")
    void everyInAppKindHasMessage() {
        Map<String, String> params =
                new HashMap<>(
                        Map.of(
                                "orderName", "가".repeat(100),
                                "amount", "1000",
                                "productName", "가".repeat(100),
                                "lineCount", "3",
                                "trackingNumber", "123456789012",
                                "type", "RETURN",
                                "refundAmount", "1000",
                                "mediated", "false",
                                "reason", "가".repeat(500),
                                "storeName", "가".repeat(100)));
        params.put("code", "NOT_CANCELABLE_AMOUNT");
        params.put("rating", "5");
        params.put("periodStart", "2026-10-25T15:00:00Z");
        params.put("periodEnd", "2026-11-01T15:00:00Z");
        for (NotificationKind kind :
                EnumSet.complementOf(EnumSet.of(NotificationKind.EMAIL_VERIFICATION))) {
            assertThat(InAppMessages.compose(kind, params))
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(500);
        }
    }
}
