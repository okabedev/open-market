package com.openmarket.notification.command.application;

import java.time.Instant;

/**
 * 보관 기간이 지난 알림을 한 묶음 지운다.
 *
 * @param before 이 시각보다 먼저 만든 알림을 지운다
 * @param limit 한 번에 지울 최대 수
 */
public record PurgeNotificationsCommand(Instant before, int limit) {}
