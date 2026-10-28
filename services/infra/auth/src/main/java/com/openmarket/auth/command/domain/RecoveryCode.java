package com.openmarket.auth.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * 복구 코드(docs/glossary.md "2단계 인증"). 인증 앱 대신 한 번 쓴다. 원문은 등록할 때 한 번만 보여 주고 해시만 저장한다.
 *
 * <p>형식은 {@code XXXXX-XXXXX}(Base32 문자 10개, 50비트)다. 틀린 입력은 로그인 실패로 세어 5회째에 잠그므로 추측할 수 없다.
 */
@Embeddable
public class RecoveryCode {

    static final int COUNT = 10;
    private static final int LENGTH = 10;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    protected RecoveryCode() {}

    RecoveryCode(String codeHash) {
        this.codeHash = codeHash;
    }

    /** 새 복구 코드 {@value #COUNT}개(원문). */
    public static List<String> generate(SecureRandom random) {
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < COUNT) {
            StringBuilder code = new StringBuilder(LENGTH + 1);
            for (int i = 0; i < LENGTH; i++) {
                if (i == LENGTH / 2) {
                    code.append('-');
                }
                code.append(Base32.ALPHABET.charAt(random.nextInt(Base32.ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return List.copyOf(codes);
    }

    /** 해시하고 비교하는 형태. 대소문자, 하이픈, 공백을 무시한다. */
    public static String normalize(String input) {
        return input.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    /** 입력이 TOTP 코드(숫자 6자리)가 아니라 복구 코드처럼 보이는지. */
    public static boolean looksLike(String input) {
        return normalize(input).matches("[A-Z2-7]{" + LENGTH + "}");
    }

    boolean isUsed() {
        return usedAt != null;
    }

    String codeHash() {
        return codeHash;
    }

    void markUsed(Instant now) {
        this.usedAt = now;
    }
}
