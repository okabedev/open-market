package com.openmarket.fixture;

import module java.base;

/** 포매터·Error Prone·NullAway가 Java 25 문법을 처리하는지 확인하는 픽스처. */
public final class Java25Syntax {

    private Java25Syntax() {}

    sealed interface Shape permits Circle, Square {}

    record Circle(long radius) implements Shape {}

    record Square(long side) implements Shape {}

    static long area(Shape shape) {
        return switch (shape) {
            case Circle(var radius) -> 3 * radius * radius;
            case Square(long side) -> side * side;
        };
    }

    static int countEntries(Map<String, Integer> values) {
        int count = 0;
        for (var _ : values.entrySet()) {
            count++;
        }
        return count;
    }

    static List<String> nonEmpty(List<String> values) {
        return values.stream().filter(value -> !value.isEmpty()).toList();
    }

    static class Base {
        private final long value;

        Base(long value) {
            this.value = value;
        }

        long value() {
            return value;
        }
    }

    static final class NonNegative extends Base {
        NonNegative(long value) {
            if (value < 0) {
                throw new IllegalArgumentException("음수는 허용하지 않는다");
            }
            super(value);
        }
    }
}
