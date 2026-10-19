package com.openmarket.file.ui.web;

import com.openmarket.file.command.application.UploadFileUseCase;
import java.util.List;
import java.util.UUID;

/**
 * 올린 파일. 상품을 저장할 때 {@code fileId}를 넣는다.
 *
 * @param width EXIF 방향을 반영한 원본 폭
 * @param variants 공개 변형. 원본은 제공하지 않는다
 */
public record UploadFileResponse(UUID fileId, int width, int height, List<Variant> variants) {

    /**
     * 공개 변형 하나.
     *
     * @param name 변형 이름. 예: {@code w300}
     * @param url gateway 기준 경로. 토큰 없이 받는다. 예: {@code /files/{fileId}/w300}
     */
    public record Variant(String name, String url, int width, int height) {}

    static UploadFileResponse of(UploadFileUseCase.UploadedFile file) {
        return new UploadFileResponse(
                file.fileId(),
                file.width(),
                file.height(),
                file.variants().stream()
                        .map(
                                variant ->
                                        new Variant(
                                                variant.name(),
                                                FileQueryController.variantUrl(
                                                        file.fileId(), variant.name()),
                                                variant.width(),
                                                variant.height()))
                        .toList());
    }
}
