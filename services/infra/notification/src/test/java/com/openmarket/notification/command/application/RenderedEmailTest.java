package com.openmarket.notification.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenderedEmailTest {

    @Test
    @DisplayName("toString은 본문을 남기지 않는다. 본문에 이름과 인증 링크가 들어간다(NFR-7)")
    void toStringHidesBody() {
        EmailRendererPort.RenderedEmail email =
                new EmailRendererPort.RenderedEmail(
                        "이메일 인증",
                        "<p>홍길동님, https://shop.test/verify?token=secret-token</p>",
                        "홍길동님, https://shop.test/verify?token=secret-token");

        assertThat(email.toString())
                .contains("이메일 인증")
                .doesNotContain("홍길동")
                .doesNotContain("secret-token");
    }
}
