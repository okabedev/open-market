package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 판매 중이 아닌 상품의 SKU다(판매 중지, 제재, 등록 중). 422다. SKU 목록을 응답에 싣는다. */
public class SkuNotOnSaleException extends BusinessException {

    private final List<UUID> skuIds;

    public SkuNotOnSaleException(List<UUID> skuIds) {
        super(ErrorType.UNPROCESSABLE, "ORDER_SKU_NOT_ON_SALE", "판매 중인 상품이 아닙니다.");
        this.skuIds = List.copyOf(skuIds);
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("skuIds", skuIds);
    }
}
