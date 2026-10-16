package com.openmarket.common.core.privacy;

/** 로그와 응답에 개인정보를 노출하지 않기 위한 마스킹 규칙(NFR-7). */
public final class Masking {

    private static final char MASK = '*';

    private Masking() {}

    /** 홍길동 → 홍*동, 홍길 → 홍*, 남궁민수 → 남**수. */
    public static String name(String name) {
        int length = name.codePointCount(0, name.length());
        if (length <= 1) {
            return "*";
        }
        int[] codePoints = name.codePoints().toArray();
        StringBuilder masked = new StringBuilder().appendCodePoint(codePoints[0]);
        if (length == 2) {
            return masked.append(MASK).toString();
        }
        masked.repeat(MASK, length - 2);
        return masked.appendCodePoint(codePoints[length - 1]).toString();
    }

    /** 가운데 자리를 가린다. 010-1234-5678 → 010-****-5678, 02-123-4567 → 02-***-4567. */
    public static String phone(String phone) {
        String[] parts = phone.trim().split("-", -1);
        if (parts.length == 3) {
            return parts[0] + "-" + String.valueOf(MASK).repeat(parts[1].length()) + "-" + parts[2];
        }
        String digits = phone.replaceAll("\\D", "");
        int headLength;
        if (digits.length() == 11) {
            headLength = 3;
        } else if (digits.length() == 10) {
            headLength = digits.startsWith("02") ? 2 : 3;
        } else {
            return keepLast(digits, 4);
        }
        int middleLength = digits.length() - headLength - 4;
        return digits.substring(0, headLength)
                + "-"
                + String.valueOf(MASK).repeat(middleLength)
                + "-"
                + digits.substring(digits.length() - 4);
    }

    /** buyer@example.com → bu***@example.com. */
    public static String email(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /** 110-123-456789 → ********6789. */
    public static String accountNumber(String accountNumber) {
        return keepLast(accountNumber.replaceAll("\\D", ""), 4);
    }

    /** 서울특별시 강남구 테헤란로 123 → 서울특별시 강남구 ***. 앞의 두 단어만 남긴다. */
    public static String address(String address) {
        String[] words = address.trim().split("\\s+", -1);
        if (words.length <= 2) {
            return words[0] + " ***";
        }
        return words[0] + " " + words[1] + " ***";
    }

    private static String keepLast(String value, int visible) {
        if (value.length() <= visible) {
            return String.valueOf(MASK).repeat(value.length());
        }
        return String.valueOf(MASK).repeat(value.length() - visible)
                + value.substring(value.length() - visible);
    }
}
