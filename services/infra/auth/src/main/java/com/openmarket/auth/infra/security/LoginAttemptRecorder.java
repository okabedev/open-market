package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.RecordLoginFailureCommand;
import com.openmarket.auth.command.application.RecordLoginFailureUseCase;
import com.openmarket.auth.command.application.RecordLoginSuccessCommand;
import com.openmarket.auth.command.application.RecordLoginSuccessUseCase;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.User;

/**
 * Spring Security 인증 이벤트로 로그인 결과를 기록한다(ACC-1 잠금).
 *
 * <ul>
 *   <li>비밀번호가 틀림: 폼에 입력한 이메일로 실패를 센다. 없는 계정이면 유스케이스가 무시한다.
 *   <li>폼 로그인 성공: 주체 이름(accountId)으로 실패 횟수를 지운다. 토큰 요청 같은 클라이언트 인증 성공은 건너뛴다.
 * </ul>
 */
public class LoginAttemptRecorder {

    private final RecordLoginFailureUseCase failure;
    private final RecordLoginSuccessUseCase success;

    public LoginAttemptRecorder(
            RecordLoginFailureUseCase failure, RecordLoginSuccessUseCase success) {
        this.failure = failure;
        this.success = success;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken) {
            failure.execute(new RecordLoginFailureCommand(event.getAuthentication().getName()));
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof User) {
            success.execute(new RecordLoginSuccessCommand(UUID.fromString(token.getName())));
        }
    }
}
