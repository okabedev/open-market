package com.openmarket.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.application.NotifyInAppCommand;
import com.openmarket.notification.command.application.ReadNotificationCommand;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회용·커맨드용 enum은 도메인 enum과 상수가 같다(conventions.md "경계를 넘는 타입"). */
class QueryEnumsTest {

    @Test
    @DisplayName("알림 종류·받는 쪽·이동 대상의 조회용 상수는 도메인과 같다")
    void sameQueryConstants() {
        assertThat(names(com.openmarket.notification.query.dto.NotificationKind.values()))
                .containsExactly(
                        names(
                                com.openmarket.notification.command.domain.NotificationKind
                                        .values()));
        assertThat(names(com.openmarket.notification.query.dto.NotificationAudience.values()))
                .containsExactly(
                        names(
                                com.openmarket.notification.command.domain.NotificationAudience
                                        .values()));
        assertThat(names(com.openmarket.notification.query.dto.NotificationTarget.values()))
                .containsExactly(
                        names(
                                com.openmarket.notification.command.domain.NotificationTarget
                                        .values()));
    }

    @Test
    @DisplayName("커맨드의 인앱 종류·받는 쪽·이동 대상은 도메인에 있는 상수다(인앱 종류는 이메일 종류를 뺀 전부)")
    void sameCommandConstants() {
        assertThat(names(NotifyInAppCommand.Kind.values()))
                .containsExactly(
                        Arrays.stream(
                                        com.openmarket.notification.command.domain.NotificationKind
                                                .values())
                                .map(Enum::name)
                                .filter(name -> !name.equals("EMAIL_VERIFICATION"))
                                .toArray(String[]::new));
        assertThat(names(NotifyInAppCommand.Audience.values()))
                .containsExactly(
                        names(
                                com.openmarket.notification.command.domain.NotificationAudience
                                        .values()));
        assertThat(names(ReadNotificationCommand.Audience.values()))
                .containsExactly(
                        names(
                                com.openmarket.notification.command.domain.NotificationAudience
                                        .values()));
        assertThat(names(NotifyInAppCommand.Target.values()))
                .containsExactly(
                        names(
                                com.openmarket.notification.command.domain.NotificationTarget
                                        .values()));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }
}
