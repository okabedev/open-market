package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** catalog에 없는 SKU다. 화면이 표시하도록 SKU 목록을 응답에 싣는다. */
public class SkuNotFoundException extends BusinessException {

    private final List<UUID> skuIds;

    public SkuNotFoundException(List<UUID> skuIds) {
        super(ErrorType.NOT_FOUND, "ORDER_SKU_NOT_FOUND", "상품을 찾을 수 없습니다.");
        this.skuIds = List.copyOf(skuIds);
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("skuIds", skuIds);
    }
}
