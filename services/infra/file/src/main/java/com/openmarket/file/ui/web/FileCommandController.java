package com.openmarket.file.ui.web;

import com.openmarket.file.command.application.UploadFileCommand;
import com.openmarket.file.command.application.UploadFileUseCase;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 판매자 업로드(CAT-1). 주체는 토큰의 {@code sub}(= memberId)다. */
@RestController
@RequestMapping("/v1/seller/files")
public class FileCommandController {

    private final UploadFileUseCase uploadFile;

    public FileCommandController(UploadFileUseCase uploadFile) {
        this.uploadFile = uploadFile;
    }

    /**
     * 상품 이미지를 올린다. 올린 파일은 상품에 붙기 전까지 {@code TEMP}이고, 24시간 안에 붙지 않으면 지운다.
     *
     * <p>JPEG, PNG, WebP를 내용으로 판별한다. 10MB를 넘으면 413, 형식이 다르면 400 {@code FILE_UNSUPPORTED_FORMAT}, 읽을
     * 수 없으면 400 {@code FILE_INVALID_IMAGE}, 5천만 화소를 넘으면 400 {@code FILE_IMAGE_TOO_LARGE}, 1시간에
     * 200개를 넘으면 429 {@code FILE_UPLOAD_QUOTA_EXCEEDED}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadFileResponse upload(
            Principal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam SellerUploadPurpose purpose) {
        return UploadFileResponse.of(
                uploadFile.execute(
                        new UploadFileCommand(
                                UUID.fromString(principal.getName()),
                                UploadFileCommand.Purpose.valueOf(purpose.name()),
                                file.getSize(),
                                file::getInputStream)),
                FileQueryController::variantUrl);
    }

    /** 판매자 경로가 받는 용도. 다른 값이면 400 {@code COMMON_INVALID_REQUEST}. */
    public enum SellerUploadPurpose {
        PRODUCT_IMAGE
    }
}
