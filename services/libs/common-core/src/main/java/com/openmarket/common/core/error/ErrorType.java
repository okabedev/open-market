package com.openmarket.common.core.error;

/** 오류 분류. 도메인은 HTTP 상태를 모르고 이 분류만 정한다. HTTP 상태로 바꾸는 일은 {@code common-web}이 한다. */
public enum ErrorType {
    /** 입력 형식이나 값이 잘못됐다. */
    INVALID,
    /** 인증이 필요하다. */
    UNAUTHORIZED,
    /** 권한이 없다. */
    FORBIDDEN,
    /** 대상이 없다. */
    NOT_FOUND,
    /** 현재 상태와 충돌한다(동시 수정, 중복 요청 처리 중 등). */
    CONFLICT,
    /** 형식은 맞지만 비즈니스 규칙상 처리할 수 없다. */
    UNPROCESSABLE,
    /** 정해진 기간의 한도를 넘었다(업로드 수 등). 잠시 뒤 다시 할 수 있다. */
    TOO_MANY_REQUESTS,
    /** 의존하는 시스템을 일시적으로 쓸 수 없다. */
    UNAVAILABLE
}
