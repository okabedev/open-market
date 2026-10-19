package com.openmarket.file.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 내용이 받는 형식(JPEG, PNG, WebP)이 아니다. */
public class UnsupportedFileFormatException extends BusinessException {

    public UnsupportedFileFormatException() {
        super(ErrorType.INVALID, "FILE_UNSUPPORTED_FORMAT", "JPEG, PNG, WebP 이미지만 올릴 수 있습니다.");
    }
}
