package com.openmarket.auth.command.application;

import com.openmarket.auth.command.application.VerifySecondFactorUseCase.Result;
import com.openmarket.auth.command.application.VerifySecondFactorUseCase.Status;
import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.RecoveryCode;
import com.openmarket.auth.command.domain.TotpCredential;
import com.openmarket.auth.command.domain.TotpCredentialRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록 화면의 코드로 2단계 인증 등록을 마친다. 맞으면 복구 코드 10개를 만들어 해시만 저장하고 원문은 한 번 돌려준다. 틀린 코드는 로그인 실패로 센다({@link
 * VerifySecondFactorUseCase}와 같다).
 */
@Service
public class ConfirmTotpEnrollmentUseCase {

    private final AccountRepository accounts;
    private final TotpCredentialRepository credentials;
    private final TotpSecretCipherPort cipher;
    private final RecoveryCodeHasherPort hasher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ConfirmTotpEnrollmentUseCase(
            AccountRepository accounts,
            TotpCredentialRepository credentials,
            TotpSecretCipherPort cipher,
            RecoveryCodeHasherPort hasher,
            Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.cipher = cipher;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public Result execute(ConfirmTotpEnrollmentCommand command) {
        Instant now = clock.instant();
        Account account = accounts.findForUpdateById(command.accountId()).orElseThrow();
        if (account.isLocked(now)) {
            return Result.of(Status.LOCKED);
        }
        Optional<TotpCredential> pending =
                credentials.findForUpdateByAccountId(account.id()).filter(c -> !c.isActive());
        if (pending.isEmpty()) {
            return Result.of(Status.NOT_ENROLLED);
        }
        TotpCredential credential = pending.get();
        List<String> codes = RecoveryCode.generate(random);
        List<String> hashes =
                codes.stream().map(code -> hasher.hash(RecoveryCode.normalize(code))).toList();
        byte[] secret = cipher.decrypt(credential.encryptedSecret());
        if (!credential.confirm(secret, command.code().strip(), hashes, now)) {
            return VerifySecondFactorUseCase.failure(account, now);
        }
        account.recordLoginSuccess();
        return new Result(Status.VERIFIED, codes);
    }
}
