package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.auth.command.application.ConfirmTotpEnrollmentCommand;
import com.openmarket.auth.command.application.PrepareTotpEnrollmentUseCase;
import com.openmarket.auth.command.application.VerifySecondFactorCommand;
import com.openmarket.auth.command.domain.AccountRegistered;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("개인정보와 2단계 인증 값의 toString은 계정 ID만 남긴다(NFR-7)")
class PersonalDataToStringTest {

    private final UUID accountId = UUID.randomUUID();

    @Test
    @DisplayName("가입 이벤트는 이메일과 이름을 남기지 않는다")
    void accountRegistered() {
        AccountRegistered event = new AccountRegistered(accountId, "buyer@example.com", "홍길동");

        assertThat(event.toString())
                .contains(accountId.toString())
                .doesNotContain("buyer@example.com")
                .doesNotContain("홍길동");
    }

    @Test
    @DisplayName("TOTP 등록 확인과 2단계 확인 명령은 코드를 남기지 않는다")
    void secondFactorCodes() {
        assertThat(new ConfirmTotpEnrollmentCommand(accountId, "482913").toString())
                .contains(accountId.toString())
                .doesNotContain("482913");
        assertThat(new VerifySecondFactorCommand(accountId, "ABCD-EFGH-2345").toString())
                .contains(accountId.toString())
                .doesNotContain("ABCD-EFGH-2345");
    }

    @Test
    @DisplayName("TOTP 등록 준비 결과는 비밀 값과 otpauth 주소를 남기지 않는다")
    void totpEnrollmentSecret() {
        PrepareTotpEnrollmentUseCase.Result result =
                new PrepareTotpEnrollmentUseCase.Result(
                        "JBSWY3DPEHPK3PXP",
                        "otpauth://totp/OpenMarket:admin?secret=JBSWY3DPEHPK3PXP");

        assertThat(result.toString()).doesNotContain("JBSWY3DPEHPK3PXP").doesNotContain("otpauth");
    }
}
