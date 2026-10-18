package com.openmarket.auth.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** 로그인 이메일. 앞뒤 공백을 지우고 소문자로 정규화해, 대소문자만 다른 이메일로 계정을 둘 만들지 못하게 한다. */
@Embeddable
public class EmailAddress {

    private static final int MAX_LENGTH = 320;
    private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    @Column(name = "email", nullable = false, length = MAX_LENGTH)
    private String value;

    protected EmailAddress() {}

    private EmailAddress(String value) {
        this.value = value;
    }

    public static EmailAddress of(String raw) {
        String normalized = raw.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_LENGTH || !FORMAT.matcher(normalized).matches()) {
            throw new InvalidEmailException();
        }
        return new EmailAddress(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmailAddress other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "EmailAddress[***]";
    }
}
