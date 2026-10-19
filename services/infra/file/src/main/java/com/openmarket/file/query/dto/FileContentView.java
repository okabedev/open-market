package com.openmarket.file.query.dto;

import java.nio.file.Path;

/**
 * 제공할 파일 내용의 위치.
 *
 * @param path 저장소의 파일 경로
 * @param contentType 응답 Content-Type
 */
public record FileContentView(Path path, String contentType, long sizeBytes) {}
