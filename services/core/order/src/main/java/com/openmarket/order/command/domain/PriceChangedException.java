package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 주문서의 기대 가격과 지금 가격이 다르다(ORD-2). 409다. 바뀐 줄을 응답에 싣는다. */
public class PriceChangedException extends BusinessException {

    private final List<Change> changes;

    public PriceChangedException(List<Change> changes) {
        super(ErrorType.CONFLICT, "ORDER_PRICE_CHANGED", "가격이 바뀐 상품이 있습니다. 주문서를 다시 확인해 주세요.");
        this.changes = List.copyOf(changes);
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("changes", changes);
    }

    /** 가격이 바뀐 줄. */
    public record Change(UUID skuId, long expectedPrice, long currentPrice) {}
}
