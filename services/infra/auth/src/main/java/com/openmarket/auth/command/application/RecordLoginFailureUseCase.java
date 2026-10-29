package com.openmarket.auth.command.application;

import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.InvalidEmailException;
import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 비밀번호가 틀린 로그인을 센다. 5회 연속이면 계정을 잠근다(ACC-1). 행을 잠그고 세어 동시에 실패해도 횟수를 잃지 않는다. */
@Service
public class RecordLoginFailureUseCase {

    private final AccountRepository accounts;
    private final Clock clock;

    public RecordLoginFailureUseCase(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Result execute(RecordLoginFailureCommand command) {
        EmailAddress email;
        try {
            email = EmailAddress.of(command.email());
        } catch (InvalidEmailException e) {
            return new Result(null, false);
        }
        return accounts.findForUpdateByEmail(email)
                .map(
                        account ->
                                new Result(
                                        account.id(), account.recordLoginFailure(clock.instant())))
                .orElse(new Result(null, false));
    }

    /**
     * 실패를 센 결과. 보안 이벤트 로그가 쓴다.
     *
     * @param accountId 계정. 없는 계정이나 형식이 틀린 이메일이면 null
     * @param locked 이번 실패로 계정을 잠갔는지
     */
    public record Result(@Nullable UUID accountId, boolean locked) {}
}
