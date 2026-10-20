package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 판매 가능 수량이 주문 수량보다 적다. 422다. 재고는 catalog 예약이 확정하므로 여기서는 빨리 알리기만 한다. */
public class InsufficientStockException extends BusinessException {

    private final List<UUID> skuIds;

    public InsufficientStockException(List<UUID> skuIds) {
        super(ErrorType.UNPROCESSABLE, "ORDER_INSUFFICIENT_STOCK", "재고가 부족한 상품이 있습니다.");
        this.skuIds = List.copyOf(skuIds);
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("skuIds", skuIds);
    }
}
