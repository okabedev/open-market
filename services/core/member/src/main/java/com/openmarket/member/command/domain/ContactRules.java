package com.openmarket.member.command.domain;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** 이름, 전화번호, 주소 입력 규칙. 오류 메시지에 입력값을 넣지 않는다(NFR-7). */
final class ContactRules {

    /** 휴대폰(010-1234-5678), 서울(02-123-4567), 지역 번호(031-123-4567). 대표 번호(1588-…)는 받지 않는다. */
    private static final Pattern PHONE = Pattern.compile("0\\d{1,2}-\\d{3,4}-\\d{4}");

    private static final Pattern ZIP_CODE = Pattern.compile("\\d{5}");

    private static final int NAME_MAX_LENGTH = 50;

    private ContactRules() {}

    static String name(@Nullable String name) {
        return requiredText(name, "이름", NAME_MAX_LENGTH);
    }

    static String phone(@Nullable String phone) {
        String value = phone == null ? "" : phone.strip();
        if (!PHONE.matcher(value).matches()) {
            throw new InvalidMemberInputException("전화번호는 010-1234-5678 형식으로 입력해 주세요.");
        }
        return value;
    }

    static @Nullable String optionalPhone(@Nullable String phone) {
        return phone == null || phone.isBlank() ? null : phone(phone);
    }

    static String zipCode(@Nullable String zipCode) {
        String value = zipCode == null ? "" : zipCode.strip();
        if (!ZIP_CODE.matcher(value).matches()) {
            throw new InvalidMemberInputException("우편번호는 숫자 5자리입니다.");
        }
        return value;
    }

    static String requiredText(@Nullable String text, String field, int maxLength) {
        String value = optionalText(text, field, maxLength);
        if (value == null) {
            throw new InvalidMemberInputException(field + "을(를) 입력해 주세요.");
        }
        return value;
    }

    static @Nullable String optionalText(@Nullable String text, String field, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new InvalidMemberInputException(field + "은(는) " + maxLength + "자까지 입력할 수 있습니다.");
        }
        return value;
    }
}
