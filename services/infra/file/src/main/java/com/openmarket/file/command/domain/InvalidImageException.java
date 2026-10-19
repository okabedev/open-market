package com.openmarket.file.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 형식은 맞지만 이미지를 읽을 수 없다(손상, 잘림). */
public class InvalidImageException extends BusinessException {

    public InvalidImageException() {
        super(ErrorType.INVALID, "FILE_INVALID_IMAGE", "이미지를 읽을 수 없습니다. 손상되지 않은 파일인지 확인해 주세요.");
    }

    public InvalidImageException(Throwable cause) {
        super(
                ErrorType.INVALID,
                "FILE_INVALID_IMAGE",
                "이미지를 읽을 수 없습니다. 손상되지 않은 파일인지 확인해 주세요.",
                cause);
    }
}
