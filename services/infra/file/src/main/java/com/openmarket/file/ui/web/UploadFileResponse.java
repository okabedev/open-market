package com.openmarket.file.ui.web;

import com.openmarket.file.command.application.UploadFileUseCase;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * 올린 파일. 상품이나 클레임을 저장할 때 {@code fileId}를 넣는다.
 *
 * @param width EXIF 방향을 반영한 원본 폭
 * @param variants 변형. 원본은 제공하지 않는다
 */
public record UploadFileResponse(UUID fileId, int width, int height, List<Variant> variants) {

    /**
     * 변형 하나.
     *
     * @param name 변형 이름. 예: {@code w300}
     * @param url gateway 기준 경로. 상품 이미지는 토큰 없이 받는 {@code /files/{fileId}/w300}, 클레임 사진은 토큰이 있어야 하는
     *     {@code /api/file/v1/files/{fileId}/w300}이다
     */
    public record Variant(String name, String url, int width, int height) {}

    static UploadFileResponse of(
            UploadFileUseCase.UploadedFile file, BiFunction<UUID, String, String> variantUrl) {
        return new UploadFileResponse(
                file.fileId(),
                file.width(),
                file.height(),
                file.variants().stream()
                        .map(
                                variant ->
                                        new Variant(
                                                variant.name(),
                                                variantUrl.apply(file.fileId(), variant.name()),
                                                variant.width(),
                                                variant.height()))
                        .toList());
    }
}
