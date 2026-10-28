package com.openmarket.auth.command.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * 관리자 2단계 인증 등록(docs/architecture/auth.md "관리자 2단계 인증"). 계정마다 하나다.
 *
 * <p>등록 화면을 처음 열면 {@code PENDING}으로 비밀 값을 만들고(다시 열면 같은 값), 코드 하나가 맞으면 {@code ACTIVE}가 되며 복구 코드를
 * 받는다. 비밀 값은 암호화한 문자열로만 갖는다(풀기는 유스케이스가 포트로 한다). 마지막으로 받은 단계를 저장해 같은 코드를 다시 받지 않는다.
 */
@Entity
@Table(name = "totp_credential")
public class TotpCredential {

    enum Status {
        PENDING,
        ACTIVE
    }

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "secret", nullable = false)
    private String encryptedSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "last_used_step")
    private @Nullable Long lastUsedStep;

    @Column(name = "enrolled_at")
    private @Nullable Instant enrolledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "totp_recovery_code", joinColumns = @JoinColumn(name = "account_id"))
    private List<RecoveryCode> recoveryCodes = new ArrayList<>();

    @Version private long version;

    protected TotpCredential() {}

    private TotpCredential(UUID accountId, String encryptedSecret, Instant now) {
        this.accountId = accountId;
        this.encryptedSecret = encryptedSecret;
        this.status = Status.PENDING;
        this.updatedAt = now;
    }

    public static TotpCredential pending(UUID accountId, String encryptedSecret, Instant now) {
        return new TotpCredential(accountId, encryptedSecret, now);
    }

    /** 코드 하나로 등록을 확인한다. 맞으면 {@code ACTIVE}가 되고 복구 코드 해시를 갖는다. */
    public boolean confirm(
            byte[] secret, String code, List<String> recoveryCodeHashes, Instant now) {
        if (status == Status.ACTIVE) {
            throw new IllegalStateException("이미 등록했다");
        }
        OptionalLong step = Totp.match(secret, code, now, lastUsedStep);
        if (step.isEmpty()) {
            return false;
        }
        this.status = Status.ACTIVE;
        this.lastUsedStep = step.getAsLong();
        this.enrolledAt = now;
        this.updatedAt = now;
        this.recoveryCodes =
                new ArrayList<>(recoveryCodeHashes.stream().map(RecoveryCode::new).toList());
        return true;
    }

    /** 로그인할 때 코드를 확인한다. 맞으면 그 단계를 기억해 같은 코드를 다시 받지 않는다. */
    public boolean verify(byte[] secret, String code, Instant now) {
        requireActive();
        OptionalLong step = Totp.match(secret, code, now, lastUsedStep);
        if (step.isEmpty()) {
            return false;
        }
        this.lastUsedStep = step.getAsLong();
        this.updatedAt = now;
        return true;
    }

    /** 쓰지 않은 복구 코드 중 해시가 맞는 것을 쓴다. */
    public boolean useRecoveryCode(Predicate<String> matchesHash, Instant now) {
        requireActive();
        for (RecoveryCode code : recoveryCodes) {
            if (!code.isUsed() && matchesHash.test(code.codeHash())) {
                code.markUsed(now);
                this.updatedAt = now;
                return true;
            }
        }
        return false;
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("등록하지 않았다");
        }
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public UUID accountId() {
        return accountId;
    }

    public String encryptedSecret() {
        return encryptedSecret;
    }

    public @Nullable Instant enrolledAt() {
        return enrolledAt;
    }

    public long remainingRecoveryCodes() {
        return recoveryCodes.stream().filter(code -> !code.isUsed()).count();
    }
}
