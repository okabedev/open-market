package com.openmarket.notification.command.domain;

/**
 * 발송 상태. {@code PENDING}은 아직 보내지 않았거나 다시 시도할 차례를 기다린다. {@code SKIPPED}는 보내지 않기로 했다(받는 회원이 없거나 탈퇴했거나
 * 이메일을 인증하지 않았다).
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
