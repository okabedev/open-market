package com.openmarket.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.order.command.application.PlaceOrderCommand;
import com.openmarket.order.query.dto.OrderDetailView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("개인정보가 든 값의 toString은 개인정보를 남기지 않는다(NFR-7)")
class PersonalDataToStringTest {

    @Test
    @DisplayName("주문 생성 명령의 배송지")
    void placeOrderCommand() {
        UUID buyerId = UUID.randomUUID();
        PlaceOrderCommand command =
                new PlaceOrderCommand(
                        buyerId,
                        List.of(),
                        new PlaceOrderCommand.Shipping(
                                "홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호"),
                        List.of(),
                        null);

        assertThat(command.toString())
                .contains(buyerId.toString())
                .doesNotContain("홍길동")
                .doesNotContain("010-1234-5678")
                .doesNotContain("테헤란로")
                .doesNotContain("101호");
    }

    @Test
    @DisplayName("주문 상세의 배송지(판매자 주문 상세도 같은 값을 쓴다)")
    void orderDetailShipping() {
        OrderDetailView.Shipping shipping =
                new OrderDetailView.Shipping(
                        "홍길동", "010-1234-5678", "06236", "서울 강남구 테헤란로 1", "101호");

        assertThat(shipping.toString())
                .doesNotContain("홍길동")
                .doesNotContain("010-1234-5678")
                .doesNotContain("테헤란로")
                .doesNotContain("101호");
    }
}
