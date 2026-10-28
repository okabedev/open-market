package com.openmarket.auth.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.auth.command.application.VerifySecondFactorUseCase.Result;
import com.openmarket.auth.command.application.VerifySecondFactorUseCase.Status;
import com.openmarket.auth.command.domain.Account;
import com.openmarket.auth.command.domain.Base32;
import com.openmarket.auth.command.domain.EmailAddress;
import com.openmarket.auth.command.domain.Totp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PrepareTotpEnrollmentUseCase}, {@link ConfirmTotpEnrollmentUseCase}, {@link
 * VerifySecondFactorUseCase}.
 */
class SecondFactorUseCaseTest {

    private final Fakes.Accounts accounts = new Fakes.Accounts();
    private final Fakes.TotpCredentials credentials = new Fakes.TotpCredentials();
    private final Fakes.MutableClock clock = new Fakes.MutableClock(Fakes.NOW);
    private final PrepareTotpEnrollmentUseCase prepare =
            new PrepareTotpEnrollmentUseCase(
                    accounts, credentials, new Fakes.PrefixCipher(), clock);
    private final ConfirmTotpEnrollmentUseCase confirm =
            new ConfirmTotpEnrollmentUseCase(
                    accounts,
                    credentials,
                    new Fakes.PrefixCipher(),
                    new Fakes.PrefixHasher(),
                    clock);
    private final VerifySecondFactorUseCase verify =
            new VerifySecondFactorUseCase(
                    accounts,
                    credentials,
                    new Fakes.PrefixCipher(),
                    new Fakes.PrefixHasher(),
                    clock);

    private Account admin;

    @BeforeEach
    void setUp() {
        admin =
                accounts.save(
                        Account.seedAdmin(
                                UUID.randomUUID(),
                                EmailAddress.of("admin@openmarket.local"),
                                "{h}",
                                Fakes.NOW));
    }

    private byte[] prepareSecret() {
        PrepareTotpEnrollmentUseCase.Result enrollment =
                prepare.execute(new PrepareTotpEnrollmentCommand(admin.id())).orElseThrow();
        return Base32.decode(enrollment.secret());
    }

    private String codeAt(byte[] secret, Instant at) {
        return Totp.code(secret, Totp.step(at));
    }

    private Result enroll(byte[] secret) {
        return confirm.execute(
                new ConfirmTotpEnrollmentCommand(admin.id(), codeAt(secret, clock.instant())));
    }

    @Test
    @DisplayName(
            "등록 준비는 비밀 값(160비트, Base32)과 인증 앱용 otpauth 주소를 준다. 등록 전에 다시 열면 같은 비밀 값이다(코드를 틀려도 다시 스캔하지 않는다)")
    void preparesEnrollment() {
        PrepareTotpEnrollmentUseCase.Result first =
                prepare.execute(new PrepareTotpEnrollmentCommand(admin.id())).orElseThrow();
        PrepareTotpEnrollmentUseCase.Result second =
                prepare.execute(new PrepareTotpEnrollmentCommand(admin.id())).orElseThrow();

        assertThat(Base32.decode(first.secret())).hasSize(20);
        assertThat(second.secret()).isEqualTo(first.secret());
        assertThat(second.otpauthUri())
                .startsWith(
                        "otpauth://totp/OpenMarket:admin%40openmarket.local?secret="
                                + second.secret())
                .contains("&issuer=OpenMarket&algorithm=SHA1&digits=6&period=30");
        assertThat(credentials.rows.get(admin.id()).encryptedSecret())
                .isEqualTo("enc:" + second.secret());
    }

    @Test
    @DisplayName("맞는 코드로 등록하면 복구 코드 10개를 한 번 주고, 등록한 계정은 등록 준비를 다시 하지 않는다")
    void confirmsEnrollment() {
        byte[] secret = prepareSecret();

        Result result = enroll(secret);

        assertThat(result.status()).isEqualTo(Status.VERIFIED);
        assertThat(result.recoveryCodes()).hasSize(10);
        assertThat(credentials.rows.get(admin.id()).isActive()).isTrue();
        assertThat(prepare.execute(new PrepareTotpEnrollmentCommand(admin.id()))).isEmpty();
    }

    @Test
    @DisplayName("로그인 확인은 코드 하나를 한 번만 받고, 다음 단계 코드는 받는다")
    void verifiesCodeOnce() {
        byte[] secret = prepareSecret();
        enroll(secret);
        clock.advance(Duration.ofSeconds(30));
        String code = codeAt(secret, clock.instant());

        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), code)).status())
                .isEqualTo(Status.VERIFIED);
        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), code)).status())
                .isEqualTo(Status.INVALID);
    }

    @Test
    @DisplayName("복구 코드는 코드 대신 한 번 쓸 수 있다(대소문자·하이픈 무시)")
    void verifiesRecoveryCodeOnce() {
        byte[] secret = prepareSecret();
        String recovery = enroll(secret).recoveryCodes().getFirst();
        String typed = recovery.toLowerCase(java.util.Locale.ROOT).replace("-", "");

        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), typed)).status())
                .isEqualTo(Status.VERIFIED);
        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), recovery)).status())
                .isEqualTo(Status.INVALID);
    }

    @Test
    @DisplayName("틀린 코드는 로그인 실패로 세어 5회째에 계정을 잠그고, 잠긴 계정은 맞는 코드도 받지 않는다")
    void locksAfterFiveWrongCodes() {
        byte[] secret = prepareSecret();
        enroll(secret);
        // 비밀번호 실패가 앞서 3번 있었다. 관리자는 비밀번호가 맞아도 횟수를 지우지 않는다.
        for (int i = 0; i < 3; i++) {
            admin.recordLoginFailure(clock.instant());
        }

        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), "000000")).status())
                .isEqualTo(Status.INVALID);
        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), "111111")).status())
                .isEqualTo(Status.LOCKED);
        assertThat(admin.isLocked(clock.instant())).isTrue();

        clock.advance(Duration.ofSeconds(30));
        assertThat(
                        verify.execute(
                                        new VerifySecondFactorCommand(
                                                admin.id(), codeAt(secret, clock.instant())))
                                .status())
                .isEqualTo(Status.LOCKED);
    }

    @Test
    @DisplayName("등록 확인의 틀린 코드도 로그인 실패로 센다. 성공하면 실패 횟수를 지운다")
    void enrollmentFailuresCountAndSuccessClears() {
        byte[] secret = prepareSecret();
        for (int i = 0; i < 4; i++) {
            assertThat(
                            confirm.execute(new ConfirmTotpEnrollmentCommand(admin.id(), "000000"))
                                    .status())
                    .isEqualTo(Status.INVALID);
        }

        assertThat(enroll(secret).status()).isEqualTo(Status.VERIFIED);
        admin.recordLoginFailure(clock.instant());
        assertThat(admin.isLocked(clock.instant())).isFalse();
    }

    @Test
    @DisplayName("등록하지 않은 계정의 로그인 확인은 NOT_ENROLLED다")
    void notEnrolled() {
        assertThat(verify.execute(new VerifySecondFactorCommand(admin.id(), "123456")).status())
                .isEqualTo(Status.NOT_ENROLLED);
    }
}
