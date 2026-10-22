package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.command.application.RequestClaimCommand;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 반품·교환 신청. 규칙(교환 사유, 설명 길이, 사진 수)은 도메인이 검사한다.
 *
 * @param type {@code RETURN} 또는 {@code EXCHANGE}
 * @param reason 사유. 교환은 판매자 귀책 사유({@code DEFECTIVE}, {@code WRONG_ITEM}, {@code NOT_AS_DESCRIBED})만
 * @param description 설명 1~500자
 * @param fileIds file에 올린 클레임 사진({@code CLAIM_IMAGE}) 0~5장. 없으면 빈 목록
 */
public record RequestClaimRequest(
        @NotNull RequestClaimCommand.Type type,
        @NotNull RequestClaimCommand.Reason reason,
        @NotNull String description,
        @Nullable List<UUID> fileIds) {}
