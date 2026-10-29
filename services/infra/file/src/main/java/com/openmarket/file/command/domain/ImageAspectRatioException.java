package com.openmarket.file.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 긴 변이 짧은 변의 {@link UploadPolicy#MAX_ASPECT_RATIO}배보다 길다. */
public class ImageAspectRatioException extends BusinessException {

    public ImageAspectRatioException() {
        super(
                ErrorType.INVALID,
                "FILE_IMAGE_ASPECT_RATIO",
                "이미지가 너무 길쭉합니다. 긴 변이 짧은 변의 10배 이하인 이미지를 올려 주세요.");
    }
}
