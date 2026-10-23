package com.openmarket.order.ui.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 주문 생성. 규칙은 docs/architecture/order.md "주문 생성", "쿠폰 적용".
 *
 * @param lines 1~50줄. 같은 SKU는 한 줄만
 * @param shipping member 배송지 목록에서 고른 배송지
 * @param issuedCouponIds 쓸 발급 쿠폰. 플랫폼 쿠폰 0~1장과 판매자마다 0~1장. 없으면 빼도 된다
 * @param expectedAmount 주문서 화면이 보여 준 결제 금액(원). 주면 지금 결제 금액과 비교해 다르면 409 {@code
 *     ORDER_AMOUNT_CHANGED}
 */
public record PlaceOrderRequest(
        @NotNull List<@Valid @NotNull Line> lines,
        @Valid @NotNull Shipping shipping,
        @Nullable List<@NotNull UUID> issuedCouponIds,
        @Nullable Long expectedAmount) {

    /**
     * 주문할 SKU 하나.
     *
     * @param quantity 1~99
     * @param expectedPrice 주문서 화면이 보여 준 개당 가격(원). 지금 가격과 다르면 409 {@code ORDER_PRICE_CHANGED}
     */
    public record Line(
            @NotNull UUID skuId, @NotNull Integer quantity, @NotNull Long expectedPrice) {}

    /**
     * 배송지.
     *
     * @param recipientPhone {@code 010-1234-5678} 형식
     * @param zipCode 숫자 5자리
     */
    public record Shipping(
            @Nullable String recipientName,
            @Nullable String recipientPhone,
            @Nullable String zipCode,
            @Nullable String baseAddress,
            @Nullable String detailAddress) {

        /** 개인정보를 남기지 않는다(NFR-7). */
        @Override
        public String toString() {
            return "Shipping[***]";
        }
    }
}
