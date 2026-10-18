package com.openmarket.auth.command.domain;

import java.util.UUID;

/** 이메일 인증 완료({@code auth.account}). 처음 인증할 때 한 번만 낸다. */
public record AccountEmailVerified(UUID accountId) {

    public static final String TYPE = "AccountEmailVerified";
}
