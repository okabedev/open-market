package com.openmarket.search.command.application;

import java.util.List;
import java.util.UUID;

/**
 * 구매확정({@code PurchaseConfirmed})의 상품별 수량.
 *
 * @param sellerOrderId 같은 하위 주문을 두 번 세지 않게 기록한다
 */
public record AddProductSalesCommand(UUID sellerOrderId, List<Line> lines) {

    public AddProductSalesCommand {
        lines = List.copyOf(lines);
    }

    /** 주문 항목 하나의 상품과 수량. */
    public record Line(UUID productId, int quantity) {}
}
