package com.openmarket.notification.command.application;

import com.openmarket.notification.command.domain.NotificationKind;
import java.util.Map;

/** 알림 종류와 값으로 메일 제목과 본문을 만든다. 구현은 Thymeleaf 템플릿(templates/mail/)이다. */
public interface EmailRendererPort {

    RenderedEmail render(NotificationKind kind, Map<String, String> params);

    record RenderedEmail(String subject, String html, String text) {

        /** 본문에 이름과 일회용 링크가 들어가므로 남기지 않는다(NFR-7). */
        @Override
        public String toString() {
            return "RenderedEmail[subject=" + subject + "]";
        }
    }
}
