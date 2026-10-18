package com.openmarket.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.function.UnaryOperator;

/** 테스트 키로 서명한 JWT. 공개 키는 JWK 세트로 띄운 HTTP 서버가 준다(실제 설정처럼 {@code jwk-set-uri}로 받는다). */
final class TestTokens {

    static final String ISSUER = "http://auth.test";

    private static final KeyPair KEY = generate();
    private static final KeyPair UNKNOWN_KEY = generate();
    private static final HttpServer JWKS = startJwks();

    private TestTokens() {}

    static String jwkSetUri() {
        return "http://localhost:" + JWKS.getAddress().getPort() + "/oauth2/jwks";
    }

    /** shop 클라이언트로 로그인한 사용자 토큰. */
    static String user(String subject, String... roles) {
        return sign(KEY, base(subject).audience("shop").claim("roles", List.of(roles)));
    }

    /** {@code client_credentials} 서비스 간 토큰. 역할이 없고 scope가 {@code internal}이다. */
    static String internal(String client) {
        return sign(KEY, base(client).audience("internal").claim("scope", "internal"));
    }

    static String user(UnaryOperator<JWTClaimsSet.Builder> customizer) {
        return sign(
                KEY,
                customizer.apply(
                        base("account-1").audience("shop").claim("roles", List.of("BUYER"))));
    }

    static String signedByUnknownKey() {
        return sign(
                UNKNOWN_KEY, base("account-1").audience("shop").claim("roles", List.of("BUYER")));
    }

    private static JWTClaimsSet.Builder base(String subject) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)));
    }

    private static String sign(KeyPair key, JWTClaimsSet.Builder claims) {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        try {
            jwt.sign(new RSASSASigner((RSAPrivateKey) key.getPrivate()));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startJwks() {
        String body =
                new JWKSet(new RSAKey.Builder((RSAPublicKey) KEY.getPublic()).build()).toString();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext(
                    "/oauth2/jwks",
                    exchange -> {
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (var out = exchange.getResponseBody()) {
                            out.write(bytes);
                        }
                    });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
