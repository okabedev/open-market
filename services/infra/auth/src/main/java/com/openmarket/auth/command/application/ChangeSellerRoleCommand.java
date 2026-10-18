package com.openmarket.auth.command.application;

import java.util.UUID;

/**
 * member의 판매자 이벤트로 {@code SELLER} 역할을 바꾼다.
 *
 * @param seller {@code true}면 붙이고(승인, 정지 해제) {@code false}면 뗀다(정지)
 */
public record ChangeSellerRoleCommand(UUID accountId, boolean seller) {}
