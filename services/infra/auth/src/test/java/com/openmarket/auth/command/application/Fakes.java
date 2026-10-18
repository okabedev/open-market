package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.EmailVerification;
import com.openmarket.auth.command.domain.EmailVerificationRepository;
import com.openmarket.common.messaging.outbox.Outbox;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 유스케이스 테스트용 가짜 포트. */
final class Fakes {

    static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    private Fakes() {}

    static Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    static final class Accounts implements AccountRepository {
        final Map<UUID, Account> rows = new LinkedHashMap<>();

        @Override
        public Optional<Account> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<Account> findByEmail(EmailAddress email) {
            return rows.values().stream().filter(a -> a.email().equals(email)).findFirst();
        }

        @Override
        public Optional<Account> findForUpdateByEmail(EmailAddress email) {
            return findByEmail(email);
        }

        @Override
        public Optional<Account> findForUpdateById(UUID id) {
            return findById(id);
        }

        @Override
        public Account save(Account account) {
            rows.put(account.id(), account);
            return account;
        }
    }

    static final class Verifications implements EmailVerificationRepository {
        final List<EmailVerification> rows = new ArrayList<>();

        @Override
        public Optional<EmailVerification> findByTokenHash(String tokenHash) {
            return rows.stream().filter(v -> v.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public EmailVerification save(EmailVerification verification) {
            rows.add(verification);
            return verification;
        }
    }

    record Appended(String aggregateType, String aggregateId, String type, Object payload) {}

    static final class RecordingOutbox implements Outbox {
        final List<Appended> events = new ArrayList<>();

        @Override
        public UUID append(
                String aggregateType,
                String aggregateId,
                String type,
                int schemaVersion,
                Object payload) {
            events.add(new Appended(aggregateType, aggregateId, type, payload));
            return UUID.randomUUID();
        }

        List<String> types() {
            return events.stream().map(Appended::type).toList();
        }
    }

    static final PasswordHasherPort HASHER = raw -> "{hashed}" + raw;

    static final VerificationLinkPort LINK =
            token -> "http://localhost:3000/verify-email?token=" + token;
}
