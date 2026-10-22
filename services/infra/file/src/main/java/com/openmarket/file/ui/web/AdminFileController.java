package com.openmarket.file.ui.web;

import com.openmarket.file.query.application.FileContentQueryService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자의 비공개 파일 제공(docs/architecture/file.md "비공개 파일"). 경로 접두어로 {@code ADMIN} 역할을 검사한다. 중재(ADM-2)에서
 * 클레임 사진을 본다. 앱의 BFF가 부르는 이미지 응답이라 OpenAPI 문서에서 뺀다.
 */
@Hidden
@RestController
@RequestMapping("/v1/admin/files")
public class AdminFileController {

    private final FileContentQueryService files;

    public AdminFileController(FileContentQueryService files) {
        this.files = files;
    }

    /** 클레임에 붙은 파일의 변형. 그 밖에는 404 {@code FILE_NOT_FOUND}. */
    @GetMapping(value = "/{fileId}/{variant}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> variant(
            @PathVariable UUID fileId, @PathVariable String variant) {
        return FileQueryController.image(
                files.findVariantForAdmin(fileId, variant), MemberFileController.PRIVATE);
    }
}
