package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.RecoveryCode;
import com.openmarket.auth.command.domain.TotpCredential;
import com.openmarket.auth.command.domain.TotpCredentialRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 다음에 2단계 인증을 확인한다(docs/architecture/auth.md "관리자 2단계 인증"). 복구 코드 형식이면 복구 코드로, 아니면 인증 앱 코드로
 * 본다. 틀리면 비밀번호 실패와 같은 횟수에 더해 5회째에 계정을 잠근다. 맞으면 실패 횟수를 지운다. 계정과 등록 행을 잠그고 확인해 같은 코드가 동시에 와도 한 번만
 * 받는다.
 */
@Service
public class VerifySecondFactorUseCase {

    private final AccountRepository accounts;
    private final TotpCredentialRepository credentials;
    private final TotpSecretCipherPort cipher;
    private final RecoveryCodeHasherPort hasher;
    private final Clock clock;

    public VerifySecondFactorUseCase(
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
    public Result execute(VerifySecondFactorCommand command) {
        Instant now = clock.instant();
        Account account = accounts.findForUpdateById(command.accountId()).orElseThrow();
        if (account.isLocked(now)) {
            return Result.of(Status.LOCKED);
        }
        Optional<TotpCredential> active =
                credentials.findForUpdateByAccountId(account.id()).filter(TotpCredential::isActive);
        if (active.isEmpty()) {
            return Result.of(Status.NOT_ENROLLED);
        }
        TotpCredential credential = active.get();
        String code = command.code().strip();
        boolean verified =
                RecoveryCode.looksLike(code)
                        ? credential.useRecoveryCode(
                                hash -> hasher.matches(RecoveryCode.normalize(code), hash), now)
                        : credential.verify(
                                cipher.decrypt(credential.encryptedSecret()), code, now);
        if (!verified) {
            return failure(account, now);
        }
        account.recordLoginSuccess();
        return Result.of(Status.VERIFIED);
    }

    /** 틀린 2단계 코드를 비밀번호 실패와 같은 횟수로 센다. 이번 실패로 잠기면 {@code LOCKED_BY_FAILURE}다. 등록 확인도 쓴다. */
    static Result failure(Account account, Instant now) {
        return Result.of(
                account.recordLoginFailure(now) ? Status.LOCKED_BY_FAILURE : Status.INVALID);
    }

    /**
     * 2단계 확인 결과. 등록 확인({@link ConfirmTotpEnrollmentUseCase})도 같은 결과를 준다.
     *
     * @param recoveryCodes 등록을 확인했을 때만 원문 10개. 다시 보여 주지 않는다
     */
    public record Result(Status status, List<String> recoveryCodes) {

        static Result of(Status status) {
            return new Result(status, List.of());
        }
    }

    public enum Status {
        /** 코드가 맞았다. 로그인을 마친다 */
        VERIFIED,
        /** 코드가 틀렸다. 로그인 실패로 셌다 */
        INVALID,
        /** 코드가 틀렸고 이번 실패로 계정을 잠갔다. 로그인 세션을 끝낸다 */
        LOCKED_BY_FAILURE,
        /** 계정이 이미 잠겨 있어 확인하지 않았다. 로그인 세션을 끝낸다 */
        LOCKED,
        /** 등록하지 않았다(또는 이미 등록해 등록 확인을 할 수 없다). 2단계 화면으로 다시 보낸다 */
        NOT_ENROLLED
    }
}
