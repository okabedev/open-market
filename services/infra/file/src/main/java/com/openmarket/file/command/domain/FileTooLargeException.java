package com.openmarket.file.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 파일이 {@link UploadPolicy#MAX_BYTES}보다 크다. */
public class FileTooLargeException extends BusinessException {

    public FileTooLargeException() {
        super(ErrorType.INVALID, "FILE_TOO_LARGE", "10MB 이하 파일만 올릴 수 있습니다.");
    }
}
