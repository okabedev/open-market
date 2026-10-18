package com.openmarket.notification.command.application;

import java.util.UUID;

/** 보낼 차례인 이메일 알림 하나. */
public record SendEmailCommand(UUID notificationId) {}
