package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.application.RecordLoginFailureCommand;
import com.openmarket.auth.command.application.RecordLoginFailureUseCase;
import com.openmarket.auth.command.application.RecordLoginSuccessCommand;
import com.openmarket.auth.command.application.RecordLoginSuccessUseCase;
import com.openmarket.auth.command.application.SecurityEventPort;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Spring Security 인증 이벤트로 로그인 결과를 기록한다(ACC-1 잠금).
 *
 * <ul>
 *   <li>비밀번호가 틀림: 폼에 입력한 이메일로 실패를 센다. 없는 계정이면 유스케이스가 무시한다. 실패와 잠금을 보안 이벤트로 남긴다 ({@link
 *       SecurityEventPort}).
 *   <li>폼 로그인 성공: 주체 이름(accountId)으로 실패 횟수를 지운다. 토큰 요청 같은 클라이언트 인증 성공은 건너뛴다. 관리자는 2단계 인증이 맞아야
 *       지운다(비밀번호 성공으로 지우면 틀린 코드 횟수가 다시 로그인할 때마다 처음부터라 코드를 끝없이 추측할 수 있다).
 * </ul>
 */
public class LoginAttemptRecorder {

    private final RecordLoginFailureUseCase failure;
    private final RecordLoginSuccessUseCase success;
    private final SecurityEventPort securityEvents;

    public LoginAttemptRecorder(
            RecordLoginFailureUseCase failure,
            RecordLoginSuccessUseCase success,
            SecurityEventPort securityEvents) {
        this.failure = failure;
        this.success = success;
        this.securityEvents = securityEvents;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token) {
            RecordLoginFailureUseCase.Result result =
                    failure.execute(new RecordLoginFailureCommand(token.getName()));
            String ip =
                    token.getDetails() instanceof WebAuthenticationDetails details
                            ? details.getRemoteAddress()
                            : null;
            securityEvents.loginFailed(result.accountId(), ip);
            UUID accountId = result.accountId();
            if (result.locked() && accountId != null) {
                securityEvents.accountLocked(accountId, ip);
            }
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof User
                && !SecondFactor.isRequired(token)) {
            success.execute(new RecordLoginSuccessCommand(UUID.fromString(token.getName())));
        }
    }
}
