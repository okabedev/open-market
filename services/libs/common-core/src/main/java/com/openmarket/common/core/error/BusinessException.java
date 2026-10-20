package com.openmarket.common.core.error;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 모든 비즈니스 예외의 베이스. 오류 분류와 오류 코드를 가진다.
 *
 * <p>오류 코드는 {@code <SERVICE>_<설명>} 형식의 대문자 snake case다. 예: {@code ORDER_PRICE_CHANGED}.
 */
public class BusinessException extends RuntimeException {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9]*(_[A-Z0-9]+)+");

    private final ErrorType type;
    private final String code;

    public BusinessException(ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = validate(code);
    }

    public BusinessException(ErrorType type, String code, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
        this.code = validate(code);
    }

    public ErrorType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    /**
     * 응답에 함께 실을 확장 필드. 화면이 어느 대상이 문제인지 보여 줄 때 쓴다(예: 가격이 바뀐 SKU 목록). 하위 클래스가 덮어쓴다. 개인정보와 입력값을 넣지
     * 않는다.
     */
    public Map<String, Object> getProperties() {
        return Map.of();
    }

    private static String validate(String code) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("오류 코드는 <SERVICE>_<설명> 형식이어야 한다: " + code);
        }
        return code;
    }
}
