package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.Map;

/** 주문서 화면이 보여 준 결제 금액과 지금 계산한 결제 금액이 다르다. 409다. 지금 금액을 응답에 싣는다. */
public class AmountChangedException extends BusinessException {

    private final long expectedAmount;
    private final long currentAmount;

    public AmountChangedException(long expectedAmount, long currentAmount) {
        super(ErrorType.CONFLICT, "ORDER_AMOUNT_CHANGED", "결제 금액이 바뀌었습니다. 주문서를 다시 확인해 주세요.");
        this.expectedAmount = expectedAmount;
        this.currentAmount = currentAmount;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("expectedAmount", expectedAmount, "currentAmount", currentAmount);
    }
}
