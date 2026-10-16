package com.openmarket.common.core.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("더하기, 빼기, 수량 곱하기를 한다")
    void arithmetic() {
        Money price = Money.won(12_000);

        assertThat(price.plus(Money.won(3_000))).isEqualTo(Money.won(15_000));
        assertThat(price.minus(Money.won(2_000))).isEqualTo(Money.won(10_000));
        assertThat(price.times(3)).isEqualTo(Money.won(36_000));
    }

    @Test
    @DisplayName("음수 금액은 만들 수 없다")
    void rejectsNegative() {
        assertThatThrownBy(() -> Money.won(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.won(1_000).minus(Money.won(1_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.won(1_000).times(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("long 범위를 넘으면 예외를 던진다")
    void rejectsOverflow() {
        assertThatThrownBy(() -> Money.won(Long.MAX_VALUE).plus(Money.won(1)))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.won(Long.MAX_VALUE).times(2))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("크기를 비교한다")
    void compares() {
        assertThat(Money.won(2).isGreaterThan(Money.won(1))).isTrue();
        assertThat(Money.won(1).isLessThan(Money.won(2))).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.won(5)).isGreaterThan(Money.won(3));
    }
}
