package com.openmarket.file.command.domain;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;

/** 업로더가 {@link UploadPolicy#QUOTA_WINDOW} 동안 {@link UploadPolicy#HOURLY_QUOTA}개를 올렸다. */
public class UploadQuotaExceededException extends BusinessException {

    public UploadQuotaExceededException() {
        super(
                ErrorType.TOO_MANY_REQUESTS,
                "FILE_UPLOAD_QUOTA_EXCEEDED",
                "업로드가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
    }
}
