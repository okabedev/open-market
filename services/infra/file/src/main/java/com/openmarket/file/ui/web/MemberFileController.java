package com.openmarket.file.ui.web;

import com.openmarket.file.command.application.UploadFileCommand;
import com.openmarket.file.command.application.UploadFileUseCase;
import com.openmarket.file.query.application.FileContentQueryService;
import io.swagger.v3.oas.annotations.Hidden;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 업로드와 비공개 파일 제공(docs/architecture/file.md "비공개 파일"). 로그인한 회원이면 부를 수 있고 주체는 토큰의 {@code sub}(=
 * memberId)다.
 */
@RestController
@RequestMapping("/v1/files")
public class MemberFileController {

    /** 비공개 파일은 브라우저에만 하루 둔다. 공유 캐시에는 남기지 않는다. */
    static final CacheControl PRIVATE = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

    private final UploadFileUseCase uploadFile;
    private final FileContentQueryService files;

    public MemberFileController(UploadFileUseCase uploadFile, FileContentQueryService files) {
        this.uploadFile = uploadFile;
        this.files = files;
    }

    /**
     * 클레임 사진을 올린다. 클레임에 붙기 전까지 {@code TEMP}이고, 24시간 안에 붙지 않으면 지운다. 검사와 오류는 판매자 업로드와 같다. 응답의 변형 URL은
     * 토큰이 있어야 받는 비공개 경로다.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadFileResponse upload(
            Principal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam MemberUploadPurpose purpose) {
        return UploadFileResponse.of(
                uploadFile.execute(
                        new UploadFileCommand(
                                UUID.fromString(principal.getName()),
                                UploadFileCommand.Purpose.valueOf(purpose.name()),
                                file.getSize(),
                                file::getInputStream)),
                MemberFileController::variantUrl);
    }

    /**
     * 비공개 파일의 변형을 받는다. 올린 사람과 파일이 붙은 클레임의 판매자만 받고, 그 밖에는 404 {@code FILE_NOT_FOUND}다. 앱의 BFF가 부르는
     * 이미지 응답이라 OpenAPI 문서에서 뺀다.
     */
    @Hidden
    @GetMapping(value = "/{fileId}/{variant}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<Resource> variant(
            Principal principal, @PathVariable UUID fileId, @PathVariable String variant) {
        return FileQueryController.image(
                files.findPrivateVariant(fileId, variant, UUID.fromString(principal.getName())),
                PRIVATE);
    }

    static String variantUrl(UUID fileId, String variant) {
        return "/api/file/v1/files/" + fileId + "/" + variant;
    }

    /** 회원 경로가 받는 용도. 다른 값이면 400 {@code COMMON_INVALID_REQUEST}. 리뷰 사진은 M5에서 더한다. */
    public enum MemberUploadPurpose {
        CLAIM_IMAGE
    }
}
