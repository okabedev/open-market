package com.openmarket.common.messaging.consumer;

/**
 * 헤더가 빠졌거나 페이로드를 읽을 수 없는 메시지. 다시 시도해도 같으므로 영구 오류로 보고 바로 DLT로 보낸다.
 *
 * <p>예외 메시지에는 페이로드 내용을 넣지 않는다. 개인정보가 로그에 남을 수 있다.
 */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
