package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.Map;

/** 지금 배송 상태에서는 할 수 없는 동작이다. 409이고 화면이 다시 그리도록 지금 상태를 함께 준다. */
public class ShipmentInvalidStateException extends BusinessException {

    private final ShipmentStatus status;

    public ShipmentInvalidStateException(ShipmentStatus status, String message) {
        super(ErrorType.CONFLICT, "FULFILLMENT_INVALID_STATE", message);
        this.status = status;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("shipmentStatus", status.name());
    }
}
