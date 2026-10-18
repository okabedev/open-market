package com.openmarket.notification.command.domain;

/** 알림 종류. 종류마다 메일 템플릿(templates/mail/)이 하나 있다. */
public enum NotificationKind {
    /** 가입 이메일 인증(NTF-1). 원인 이벤트는 {@code EmailVerificationRequested} */
    EMAIL_VERIFICATION
}
