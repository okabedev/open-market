package com.openmarket.auth.ui.web;

import java.util.UUID;

/**
 * 가입 결과.
 *
 * @param accountId 계정 ID(= memberId)
 */
public record RegisterAccountResponse(UUID accountId) {}
