package com.openmarket.auth.infra.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 동의 저장소 호출을 트랜잭션 하나로 감싼다. {@code JdbcOAuth2AuthorizationConsentService}도 행을 읽는 도중에 클라이언트를 조회한다.
 * 이유는 {@link TransactionalAuthorizationService}와 같다.
 */
class TransactionalAuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentService delegate;
    private final TransactionOperations transactions;

    TransactionalAuthorizationConsentService(
            OAuth2AuthorizationConsentService delegate, TransactionOperations transactions) {
        this.delegate = delegate;
        this.transactions = transactions;
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        transactions.executeWithoutResult(status -> delegate.save(authorizationConsent));
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        transactions.executeWithoutResult(status -> delegate.remove(authorizationConsent));
    }

    @Override
    public @Nullable OAuth2AuthorizationConsent findById(
            String registeredClientId, String principalName) {
        return transactions.execute(status -> delegate.findById(registeredClientId, principalName));
    }
}
