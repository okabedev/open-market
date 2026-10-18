package com.openmarket.notification.command.domain;

/** 발송 상태. {@code PENDING}은 아직 보내지 않았거나 다시 시도할 차례를 기다린다. */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED
}
