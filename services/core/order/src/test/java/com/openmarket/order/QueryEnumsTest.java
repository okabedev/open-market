package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회용 enum과 커맨드 입력 enum은 도메인 enum과 상수가 같다(conventions.md "경계를 넘는 타입"). */
class QueryEnumsTest {

    @Test
    @DisplayName("주문 상태, 취소 사유, 하위 주문 진행 상태·클레임의 조회용·입력용 상수는 도메인과 같다")
    void sameConstants() {
        assertThat(names(com.openmarket.order.query.dto.OrderStatus.values()))
                .containsExactly(names(com.openmarket.order.command.domain.OrderStatus.values()));
        assertThat(names(com.openmarket.order.query.dto.CancelReason.values()))
                .containsExactly(names(com.openmarket.order.command.domain.CancelReason.values()));
        assertThat(
                        names(
                                com.openmarket.order.command.application.CancelOrderCommand.Reason
                                        .values()))
                .containsExactly(names(com.openmarket.order.command.domain.CancelReason.values()));
        assertThat(names(com.openmarket.order.query.dto.SellerOrderProgress.values()))
                .containsExactly(
                        names(com.openmarket.order.command.domain.SellerOrderProgress.values()));
        // 배송 이벤트의 단계는 결제 완료(PAID)를 뺀 진행 상태다.
        assertThat(
                        names(
                                com.openmarket.order.command.application.AdvanceSellerOrderCommand
                                        .Stage.values()))
                .containsExactly("ACCEPTED", "SHIPPED", "IN_TRANSIT", "DELIVERED", "CONFIRMED");
        assertThat(names(com.openmarket.order.query.dto.ClaimType.values()))
                .containsExactly(names(com.openmarket.order.command.domain.ClaimType.values()));
        assertThat(names(com.openmarket.order.command.application.ApplyClaimCommand.Type.values()))
                .containsExactly(names(com.openmarket.order.command.domain.ClaimType.values()));
        assertThat(names(com.openmarket.order.query.dto.ClaimStatus.values()))
                .containsExactly(names(com.openmarket.order.command.domain.ClaimStatus.values()));
        assertThat(
                        names(
                                com.openmarket.order.command.application.ApplyClaimCommand.Status
                                        .values()))
                .containsExactly(names(com.openmarket.order.command.domain.ClaimStatus.values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
