package com.openmarket.common.core.error;

/** 대상이 없을 때 던진다. 커맨드와 쿼리 양쪽에서 쓴다. */
public class NotFoundException extends BusinessException {

    public NotFoundException(String code, String message) {
        super(ErrorType.NOT_FOUND, code, message);
    }
}
