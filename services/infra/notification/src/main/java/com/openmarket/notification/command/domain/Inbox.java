package com.openmarket.notification.command.domain;

import java.util.UUID;

/**
 * 알림함. 회원 한 명이 받는 쪽 하나에서 보는 인앱 알림이다. 관리자 알림함은 관리자 모두에게 같다.
 *
 * @param accountId 보는 회원. 읽음 기록의 주인이다
 */
public record Inbox(NotificationAudience audience, UUID accountId) {}
