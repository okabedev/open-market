package com.openmarket.member.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 정지·해제(SEL-3).
 *
 * @param adminId 작업한 관리자(토큰의 {@code sub}). 감사 로그에 남는다
 * @param reason 정지 사유. 정지할 때 꼭 있어야 한다
 */
public record ChangeSellerStatusCommand(UUID adminId, UUID sellerId, @Nullable String reason) {}
