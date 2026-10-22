package com.openmarket.notification.command.application;

import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationTarget;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 알림 이메일에 넣을 앱 화면 주소. 앱 주소는 설정(config-repo notification.yml)에 있다. */
public interface AppLinkPort {

    /**
     * 받는 쪽의 앱에서 이동 대상을 보여 주는 화면 주소. 대상이 없거나 화면이 없으면 앱의 알맞은 첫 화면이다.
     *
     * @param target 이동 대상 종류. 없으면 {@code null}
     */
    String linkFor(
            NotificationAudience audience,
            @Nullable NotificationTarget target,
            @Nullable UUID targetId);
}
