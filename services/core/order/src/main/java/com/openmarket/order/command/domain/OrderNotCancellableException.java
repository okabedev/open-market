package com.openmarket.order.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 결제가 끝난 주문은 구매자가 바로 취소할 수 없다. 결제 후 취소는 클레임(M4)이다. 409다. */
public class OrderNotCancellableException extends BusinessException {

    public OrderNotCancellableException() {
        super(ErrorType.CONFLICT, "ORDER_NOT_CANCELLABLE", "결제가 끝난 주문은 여기서 취소할 수 없습니다.");
    }
}
