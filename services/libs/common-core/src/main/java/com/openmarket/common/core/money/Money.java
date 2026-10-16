package com.openmarket.common.core.money;

/**
 * 원(KRW) 단위 금액. 소수점과 음수를 허용하지 않는다. 연산이 {@code long} 범위를 넘으면 예외를 던진다.
 *
 * <p>할인율처럼 나눗셈이 필요한 계산은 반올림 규칙이 도메인마다 다르므로 여기서 제공하지 않는다.
 */
public record Money(long amount) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);

    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("금액은 음수일 수 없다: " + amount);
        }
    }

    public static Money won(long amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(amount, other.amount));
    }

    /** 결과가 음수가 되면 예외를 던진다. */
    public Money minus(Money other) {
        return new Money(Math.subtractExact(amount, other.amount));
    }

    public Money times(long quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("수량은 음수일 수 없다: " + quantity);
        }
        return new Money(Math.multiplyExact(amount, quantity));
    }

    public boolean isZero() {
        return amount == 0;
    }

    public boolean isGreaterThan(Money other) {
        return amount > other.amount;
    }

    public boolean isLessThan(Money other) {
        return amount < other.amount;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(amount, other.amount);
    }
}
