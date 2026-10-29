package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.Base32;
import com.openmarket.auth.command.domain.Totp;
import com.openmarket.auth.command.domain.TotpCredential;
import com.openmarket.auth.command.domain.TotpCredentialRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 화면에 보여 줄 비밀 값을 준다(docs/architecture/auth.md "관리자 2단계 인증"). 처음이면 새로 만들어 {@code PENDING}으로 저장하고,
 * 등록 전에 다시 열면 같은 값을 준다(코드를 틀려 화면이 다시 열려도 인증 앱에 다시 등록하지 않게). 이미 등록한 계정이면 빈 값을 돌려주고 화면은 코드 입력으로 간다.
 */
@Service
public class PrepareTotpEnrollmentUseCase {

    /** 인증 앱에 보이는 발급자 이름. */
    static final String ISSUER = "OpenMarket";

    private final AccountRepository accounts;
    private final TotpCredentialRepository credentials;
    private final TotpSecretCipherPort cipher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PrepareTotpEnrollmentUseCase(
            AccountRepository accounts,
            TotpCredentialRepository credentials,
            TotpSecretCipherPort cipher,
            Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public Optional<Result> execute(PrepareTotpEnrollmentCommand command) {
        Account account = accounts.findById(command.accountId()).orElseThrow();
        Optional<TotpCredential> existing = credentials.findForUpdateByAccountId(account.id());
        if (existing.isPresent() && existing.get().isActive()) {
            return Optional.empty();
        }
        byte[] secret;
        if (existing.isPresent()) {
            secret = cipher.decrypt(existing.get().encryptedSecret());
        } else {
            secret = Totp.generateSecret(random);
            credentials.save(
                    TotpCredential.pending(account.id(), cipher.encrypt(secret), clock.instant()));
        }
        String base32 = Base32.encode(secret);
        return Optional.of(new Result(base32, otpauthUri(account, base32)));
    }

    private static String otpauthUri(Account account, String secret) {
        // 라벨은 "발급자:계정"이다. 콜론은 그대로 두고 나머지(이메일의 @ 등)는 인코딩한다.
        String label =
                ISSUER + ":" + URLEncoder.encode(account.email().value(), StandardCharsets.UTF_8);
        return "otpauth://totp/"
                + label
                + "?secret="
                + secret
                + "&issuer="
                + ISSUER
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * 등록 화면에 보여 줄 비밀 값.
     *
     * @param secret Base32(패딩 없음). 인증 앱에 직접 입력할 때 쓴다
     * @param otpauthUri 인증 앱이 QR로 읽는 {@code otpauth://totp/...} 주소
     */
    public record Result(String secret, String otpauthUri) {

        /** 비밀 값을 남기지 않는다. */
        @Override
        public String toString() {
            return "Result[***]";
        }
    }
}
