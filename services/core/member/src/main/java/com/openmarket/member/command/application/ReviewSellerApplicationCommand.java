package com.openmarket.member.command.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 입점 신청 심사(SEL-2).
 *
 * @param adminId 심사한 관리자(토큰의 {@code sub}). 감사 로그에 남는다
 * @param reason 반려 사유. 반려할 때 꼭 있어야 한다
 */
public record ReviewSellerApplicationCommand(
        UUID adminId, UUID applicationId, @Nullable String reason) {}
