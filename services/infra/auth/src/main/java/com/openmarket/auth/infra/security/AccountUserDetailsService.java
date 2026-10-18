package com.openmarket.auth.infra.security;

import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.AccountRepository;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.InvalidEmailException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 폼의 이메일로 계정을 찾는다. 인증된 주체 이름은 accountId라서 토큰의 {@code sub}가 accountId가 된다.
 *
 * <p>주체는 Spring Security {@link User}로 만든다. 인가 정보({@code oauth2_authorization.attributes})에 JSON으로
 * 저장되므로 Spring Security Jackson 모듈이 아는 타입이어야 한다.
 */
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    public AccountUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        EmailAddress address;
        try {
            address = EmailAddress.of(email);
        } catch (InvalidEmailException e) {
            throw new UsernameNotFoundException("계정이 없다");
        }
        Account account =
                accounts.findByEmail(address)
                        .orElseThrow(() -> new UsernameNotFoundException("계정이 없다"));
        return User.withUsername(account.id().toString())
                .password(account.passwordHash())
                .roles(account.roles().stream().map(Enum::name).toArray(String[]::new))
                .disabled(!account.isActive())
                .build();
    }
}
