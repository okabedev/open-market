package com.openmarket.common.core.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BusinessExceptionTest {

    @Test
    @DisplayName("오류 분류와 코드를 가진다")
    void holdsTypeAndCode() {
        var exception =
                new BusinessException(ErrorType.UNPROCESSABLE, "ORDER_PRICE_CHANGED", "가격이 바뀌었다");

        assertThat(exception.getType()).isEqualTo(ErrorType.UNPROCESSABLE);
        assertThat(exception.getCode()).isEqualTo("ORDER_PRICE_CHANGED");
        assertThat(exception).hasMessage("가격이 바뀌었다");
    }

    @Test
    @DisplayName("NotFoundException은 NOT_FOUND 분류다")
    void notFound() {
        assertThat(new NotFoundException("ORDER_NOT_FOUND", "주문이 없다").getType())
                .isEqualTo(ErrorType.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER", "order_not_found", "ORDER-NOT-FOUND", "_ORDER_X", ""})
    @DisplayName("오류 코드가 <SERVICE>_<설명> 형식이 아니면 거절한다")
    void rejectsMalformedCode(String code) {
        assertThatThrownBy(() -> new BusinessException(ErrorType.INVALID, code, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
