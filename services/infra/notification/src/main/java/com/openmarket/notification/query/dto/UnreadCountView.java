package com.openmarket.notification.query.dto;

/** 알림함의 안 읽은 인앱 알림 수. 앱 헤더가 페이지 이동 때와 주기적으로 읽는다. */
public record UnreadCountView(long count) {}
