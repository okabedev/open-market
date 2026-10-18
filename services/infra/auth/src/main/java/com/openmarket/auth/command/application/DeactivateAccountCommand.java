package com.openmarket.auth.command.application;

import java.util.UUID;

/** member의 {@code MemberWithdrawn}으로 계정을 비활성화한다. */
public record DeactivateAccountCommand(UUID accountId) {}
