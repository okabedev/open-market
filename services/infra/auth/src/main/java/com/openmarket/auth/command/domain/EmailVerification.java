package com.openmarket.auth.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 이메일 인증(docs/glossary.md). 가입과 재요청마다 하나씩 만든다. 24시간 안에 한 번 쓰면 끝난다. 이미 쓴 토큰을 다시 써도 같은 결과를 돌려준다(링크를 두
 * 번 눌러도 오류가 아니다).
 */
@Entity
@Table(name = "email_verification")
public class EmailVerification {

    static final Duration TTL = Duration.ofHours(24);

    @Id private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private @Nullable Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailVerification() {}

    private EmailVerification(UUID id, UUID accountId, String tokenHash, Instant now) {
        this.id = id;
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = now.plus(TTL);
        this.createdAt = now;
    }

    public static EmailVerification issue(
            UUID id, UUID accountId, VerificationToken token, Instant now) {
        return new EmailVerification(id, accountId, token.hash(), now);
    }

    /** 토큰을 쓰고 계정 ID를 돌려준다. 이미 썼으면 만료와 상관없이 같은 계정 ID다. */
    public UUID use(Instant now) {
        if (usedAt != null) {
            return accountId;
        }
        if (!now.isBefore(expiresAt)) {
            throw new VerificationExpiredException();
        }
        usedAt = now;
        return accountId;
    }

    public UUID accountId() {
        return accountId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
