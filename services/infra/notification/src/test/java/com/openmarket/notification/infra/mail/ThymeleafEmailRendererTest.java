package com.openmarket.notification.infra.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.domain.NotificationKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ThymeleafEmailRendererTest {

    @Test
    @DisplayName("이메일을 보내는 종류마다 제목이 있다")
    void everyEmailedKindHasSubject() {
        for (NotificationKind kind : NotificationKind.values()) {
            if (kind.emailed() || kind == NotificationKind.EMAIL_VERIFICATION) {
                assertThat(ThymeleafEmailRenderer.subject(kind))
                        .as(kind.name())
                        .startsWith("[오픈 마켓] ");
            }
        }
        assertThat(ThymeleafEmailRenderer.subject(NotificationKind.SETTLEMENT_CONFIRMED))
                .isEqualTo("[오픈 마켓] 정산이 확정됐습니다");
    }
}
