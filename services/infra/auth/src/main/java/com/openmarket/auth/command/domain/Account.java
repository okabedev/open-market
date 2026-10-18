package com.openmarket.auth.command.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 계정 집계: 로그인 수단(이메일, 비밀번호 해시)과 역할. 프로필 개인정보는 member가 갖는다(docs/glossary.md "계정").
 *
 * <p>{@code id}는 accountId(= memberId)이고 토큰의 {@code sub}가 된다.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id private UUID id;

    @Embedded private EmailAddress email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "email_verified_at")
    private @Nullable Instant emailVerifiedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "account_role", joinColumns = @JoinColumn(name = "account_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Account() {}

    private Account(
            UUID id,
            EmailAddress email,
            String passwordHash,
            Set<Role> roles,
            @Nullable Instant emailVerifiedAt,
            Instant now) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.roles = EnumSet.copyOf(roles);
        this.emailVerifiedAt = emailVerifiedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 운영자가 시드로 만드는 관리자 계정. 가입으로는 만들 수 없다(PRD 2절). */
    public static Account seedAdmin(UUID id, EmailAddress email, String passwordHash, Instant now) {
        return new Account(id, email, passwordHash, EnumSet.of(Role.ADMIN), now, now);
    }

    public UUID id() {
        return id;
    }

    public EmailAddress email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Set<Role> roles() {
        return Collections.unmodifiableSet(roles);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
