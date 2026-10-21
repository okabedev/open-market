package com.openmarket.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회용 enum은 도메인 enum과 상수가 같다(conventions.md "경계를 넘는 타입"). */
class QueryEnumsTest {

    @Test
    @DisplayName("배송 상태의 조회용 상수는 도메인과 같다")
    void sameConstants() {
        assertThat(names(com.openmarket.fulfillment.query.dto.ShipmentStatus.values()))
                .containsExactly(
                        names(com.openmarket.fulfillment.command.domain.ShipmentStatus.values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
