package com.openmarket.auth.infra.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 인가 기록 저장소 호출을 트랜잭션 하나로 감싼다. {@code JdbcOAuth2AuthorizationService}는 행을 읽는 도중에 클라이언트를 조회하는데, 트랜잭션이
 * 없으면 그 조회가 두 번째 연결을 받는다. 동시 요청이 풀 크기를 넘으면 모두가 첫 연결을 쥔 채 기다리다 시간 초과가 된다. 트랜잭션 안에서는 두 조회가 같은 연결을 쓴다.
 */
class TransactionalAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final TransactionOperations transactions;

    TransactionalAuthorizationService(
            OAuth2AuthorizationService delegate, TransactionOperations transactions) {
        this.delegate = delegate;
        this.transactions = transactions;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status -> delegate.save(authorization));
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        transactions.executeWithoutResult(status -> delegate.remove(authorization));
    }

    @Override
    public @Nullable OAuth2Authorization findById(String id) {
        return transactions.execute(status -> delegate.findById(id));
    }

    @Override
    public @Nullable OAuth2Authorization findByToken(
            String token, @Nullable OAuth2TokenType tokenType) {
        return transactions.execute(status -> delegate.findByToken(token, tokenType));
    }
}
