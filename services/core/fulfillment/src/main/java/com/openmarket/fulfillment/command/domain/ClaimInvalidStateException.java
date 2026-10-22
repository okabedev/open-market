package com.openmarket.fulfillment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.Map;

/** 지금 클레임 상태에서는 할 수 없는 동작이다. 409이고 화면이 다시 그리도록 지금 상태를 함께 준다. */
public class ClaimInvalidStateException extends BusinessException {

    private final ClaimStatus status;

    public ClaimInvalidStateException(ClaimStatus status, String message) {
        super(ErrorType.CONFLICT, "FULFILLMENT_CLAIM_INVALID_STATE", message);
        this.status = status;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("claimStatus", status.name());
    }
}
