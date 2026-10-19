package com.openmarket.file.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 이미지 픽셀 수가 {@link UploadPolicy#MAX_PIXELS}보다 많다. */
public class ImageTooLargeException extends BusinessException {

    public ImageTooLargeException() {
        super(ErrorType.INVALID, "FILE_IMAGE_TOO_LARGE", "이미지 해상도가 너무 큽니다. 5천만 화소 이하로 줄여 주세요.");
    }
}
