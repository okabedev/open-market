package com.openmarket.file.query.dto;

import java.nio.file.Path;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 제공할 파일 내용의 위치와 볼 수 있는 사람을 정하는 정보.
 *
 * @param path 저장소의 파일 경로
 * @param contentType 응답 Content-Type
 * @param purpose 파일 용도(커맨드 쪽 {@code FilePurpose}의 이름)
 * @param uploaderId 올린 사람
 * @param ownerType 붙어 있는(또는 붙어 있던) 소유자의 종류. 붙은 적이 없으면 비어 있다
 * @param ownerSellerId 소유자의 판매자. 붙은 적이 없거나 알 수 없으면 비어 있다
 */
public record FileContentView(
        Path path,
        String contentType,
        long sizeBytes,
        String purpose,
        UUID uploaderId,
        @Nullable String ownerType,
        @Nullable UUID ownerSellerId) {}
