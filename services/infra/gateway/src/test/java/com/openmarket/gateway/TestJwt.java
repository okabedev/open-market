package com.openmarket.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/** auth 대신 테스트 키로 액세스 토큰을 만든다. 클레임은 docs/architecture/auth.md의 "토큰" 표를 따른다. */
final class TestJwt {

    static final String ISSUER = "http://auth.openmarket.local:9000";

    private static final RSAKey KEY = generate("test-key");
    private static final RSAKey UNKNOWN_KEY = generate("unknown-key");

    private TestJwt() {}

    /** shop 클라이언트로 로그인한 사용자의 유효한 토큰. */
    static String token(String subject, String... roles) {
        return token(claims -> claims.subject(subject).claim("roles", List.of(roles)));
    }

    /** 유효한 기본 클레임에 바꿀 것만 덮어쓴 토큰. */
    static String token(Consumer<JWTClaimsSet.Builder> customizer) {
        return sign(KEY, customizer);
    }

    /** JWK 세트에 없는 키로 서명한 토큰. */
    static String signedByUnknownKey() {
        return sign(UNKNOWN_KEY, claims -> {});
    }

    static String jwkSetJson() {
        return new JWKSet(KEY.toPublicJWK()).toString();
    }

    private static String sign(RSAKey key, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .subject("account-1")
                        .audience("shop")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plus(Duration.ofMinutes(10))))
                        .claim("scope", "openid profile api")
                        .claim("roles", List.of("BUYER"));
        customizer.accept(claims);
        SignedJWT jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                        claims.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
