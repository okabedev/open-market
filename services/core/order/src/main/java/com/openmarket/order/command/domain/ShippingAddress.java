package com.openmarket.order.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 주문 당시 배송지 스냅샷. 규칙은 member 배송지와 같다(docs/architecture/member.md "규칙"). 오류 메시지에 입력값을 넣지 않는다 (NFR-7).
 */
@Embeddable
public class ShippingAddress {

    /** 휴대폰(010-1234-5678), 서울(02-123-4567), 지역 번호(031-123-4567). */
    private static final Pattern PHONE = Pattern.compile("0\\d{1,2}-\\d{3,4}-\\d{4}");

    private static final Pattern ZIP_CODE = Pattern.compile("\\d{5}");

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "base_address", nullable = false)
    private String baseAddress;

    @Column(name = "detail_address")
    private @Nullable String detailAddress;

    protected ShippingAddress() {}

    private ShippingAddress(
            String recipientName,
            String recipientPhone,
            String zipCode,
            String baseAddress,
            @Nullable String detailAddress) {
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipCode = zipCode;
        this.baseAddress = baseAddress;
        this.detailAddress = detailAddress;
    }

    /**
     * 입력을 확인해 만든다. 앞뒤 공백은 지운다.
     *
     * @throws InvalidOrderInputException 규칙을 어겼다
     */
    public static ShippingAddress of(
            @Nullable String recipientName,
            @Nullable String recipientPhone,
            @Nullable String zipCode,
            @Nullable String baseAddress,
            @Nullable String detailAddress) {
        return new ShippingAddress(
                requiredText(recipientName, "받는 사람", 50),
                matching(recipientPhone, PHONE, "연락처는 010-1234-5678 형식으로 입력해 주세요."),
                matching(zipCode, ZIP_CODE, "우편번호는 숫자 5자리입니다."),
                requiredText(baseAddress, "기본 주소", 200),
                optionalText(detailAddress, "상세 주소", 200));
    }

    private static String matching(@Nullable String value, Pattern pattern, String message) {
        String stripped = value == null ? "" : value.strip();
        if (!pattern.matcher(stripped).matches()) {
            throw new InvalidOrderInputException(message);
        }
        return stripped;
    }

    private static String requiredText(@Nullable String text, String field, int maxLength) {
        String value = optionalText(text, field, maxLength);
        if (value == null) {
            throw new InvalidOrderInputException(field + "을(를) 입력해 주세요.");
        }
        return value;
    }

    private static @Nullable String optionalText(
            @Nullable String text, String field, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new InvalidOrderInputException(field + "은(는) " + maxLength + "자까지 입력할 수 있습니다.");
        }
        return value;
    }

    public String recipientName() {
        return recipientName;
    }

    public String recipientPhone() {
        return recipientPhone;
    }

    public String zipCode() {
        return zipCode;
    }

    public String baseAddress() {
        return baseAddress;
    }

    public @Nullable String detailAddress() {
        return detailAddress;
    }
}
