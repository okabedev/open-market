package com.openmarket.auth.infra;

import com.openmarket.auth.command.application.PasswordHasherPort;
import com.openmarket.auth.command.application.RecoveryCodeHasherPort;
import com.openmarket.auth.command.application.SeedAdminCommand;
import com.openmarket.auth.command.application.SeedAdminUseCase;
import com.openmarket.auth.command.application.TotpSecretCipherPort;
import com.openmarket.auth.command.application.VerificationLinkPort;
import com.openmarket.auth.infra.key.AesGcmTotpSecretCipherAdapter;
import com.openmarket.auth.infra.key.JdbcSigningKeyStore;
import com.openmarket.auth.infra.key.SigningKeyCipher;
import com.openmarket.auth.infra.security.PasswordEncoderRecoveryCodeHasherAdapter;
import com.openmarket.auth.infra.security.RegisteredClientSynchronizer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.support.TransactionTemplate;

/** auth의 어댑터와 기동 작업(클라이언트 동기화, 관리자 시드). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthInfraConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthInfraConfiguration.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** 기본 bcrypt. 저장 값에 {@code {bcrypt}} 접두어가 붙어 나중에 알고리즘을 바꿀 수 있다. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    PasswordHasherPort passwordHasher(PasswordEncoder passwordEncoder) {
        return passwordEncoder::encode;
    }

    @Bean
    TotpSecretCipherPort totpSecretCipher(AuthProperties properties) {
        return new AesGcmTotpSecretCipherAdapter(properties.totpSecretKey());
    }

    @Bean
    RecoveryCodeHasherPort recoveryCodeHasher(PasswordEncoder passwordEncoder) {
        return new PasswordEncoderRecoveryCodeHasherAdapter(passwordEncoder);
    }

    /** 인증 메일 링크: shop 앱의 인증 화면 주소 + {@code ?token=}. 토큰은 Base64URL이라 인코딩할 문자가 없다. */
    @Bean
    VerificationLinkPort verificationLink(AuthProperties properties) {
        String base = properties.emailVerificationUrl().toString();
        return token -> base + "?token=" + token;
    }

    @Bean
    JdbcSigningKeyStore signingKeyStore(
            JdbcClient jdbc,
            TransactionTemplate transaction,
            AuthProperties properties,
            Clock clock) {
        return new JdbcSigningKeyStore(
                jdbc, transaction, new SigningKeyCipher(properties.signingKeySecret()), clock);
    }

    @Bean
    ApplicationRunner registeredClientSync(
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            Clock clock) {
        return args -> {
            new RegisteredClientSynchronizer(clients, passwordEncoder, properties, clock)
                    .synchronize();
            log.info(
                    "OAuth2 클라이언트 동기화: 사용자 {}, 서비스 {}",
                    properties.userClients().keySet(),
                    properties.serviceClients().keySet());
        };
    }

    @Bean
    ApplicationRunner adminSeed(SeedAdminUseCase seedAdmin, AuthProperties properties) {
        return args -> {
            AuthProperties.AdminSeed seed = properties.adminSeed();
            if (!seed.isConfigured()) {
                log.info("관리자 시드 계정 설정이 없어 만들지 않는다");
                return;
            }
            if (seedAdmin.execute(new SeedAdminCommand(seed.email(), seed.password()))) {
                log.info("관리자 시드 계정을 만들었다");
            }
        };
    }
}
