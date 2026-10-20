package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회용 enum과 커맨드 입력 enum은 도메인 enum과 상수가 같다(conventions.md "경계를 넘는 타입"). */
class QueryEnumsTest {

    @Test
    @DisplayName("주문 상태와 취소 사유의 조회용·입력용 상수는 도메인과 같다")
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
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
