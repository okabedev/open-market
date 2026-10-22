package com.openmarket.file.ui.web;

import com.openmarket.file.query.application.FileContentQueryService;
import com.openmarket.file.query.dto.FileContentView;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 이미지 제공. gateway {@code /files/**}가 토큰 없이 넘긴다(config-repo의 {@code public-paths}).
 *
 * <p>파일 내용은 바뀌지 않는다(바뀌면 새 {@code fileId}를 쓴다). 그래서 1년 동안 캐시하고 다시 확인하지 않게 한다.
 *
 * <p>브라우저가 {@code <img>}로 바로 받는 경로라 OpenAPI 문서에서 뺀다. api-client는 {@code /api/file} 아래만 부른다.
 */
@Hidden
@RestController
public class FileQueryController {

    private static final CacheControl IMMUTABLE =
            CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final FileContentQueryService files;

    public FileQueryController(FileContentQueryService files) {
        this.files = files;
    }

    /** 이미지 변형을 받는다. 원본이나 없는 변형, 없는 파일, 비공개 파일은 404 {@code FILE_NOT_FOUND}. */
    @GetMapping(value = "/files/{fileId}/{variant}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> variant(
            @PathVariable UUID fileId, @PathVariable String variant) {
        return image(files.findPublicVariant(fileId, variant), IMMUTABLE);
    }

    static ResponseEntity<Resource> image(FileContentView content, CacheControl cacheControl) {
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .body(new FileSystemResource(content.path()));
    }

    static String variantUrl(UUID fileId, String variant) {
        return "/files/" + fileId + "/" + variant;
    }
}
