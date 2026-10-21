package com.openmarket.payment.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.util.Map;

/**
 * 토스가 승인을 거절했다. 결제는 {@code FAILED}이고 주문 결제 기한 안에 다시 결제할 수 있다. 422다. 토스 오류 코드와 메시지를 싣는다. 토스 메시지는
 * 구매자에게 보여 줄 수 있는 문장이고 개인정보가 없다.
 */
public class PaymentRejectedException extends BusinessException {

    private final String pgCode;
    private final String pgMessage;

    public PaymentRejectedException(String pgCode, String pgMessage) {
        super(ErrorType.UNPROCESSABLE, "PAYMENT_REJECTED", "결제가 승인되지 않았습니다.");
        this.pgCode = pgCode;
        this.pgMessage = pgMessage;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Map.of("pgCode", pgCode, "pgMessage", pgMessage);
    }
}
