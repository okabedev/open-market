package com.openmarket.file.command.domain;

import java.time.Duration;

/** 업로드 한도(docs/architecture/file.md "검사"). */
public final class UploadPolicy {

    /** 파일 하나의 최대 크기(10MB). multipart 한도(application.yml)와 같게 둔다. */
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    /** 최대 픽셀 수. 작은 파일이 풀면 거대해지는 이미지(압축 폭탄)를 막는다. 4:3이면 약 8160x6120. */
    public static final long MAX_PIXELS = 50_000_000L;

    /** 업로더 한 명이 {@link #QUOTA_WINDOW} 동안 올릴 수 있는 파일 수. 디스크를 채우는 남용을 막는다. */
    public static final int HOURLY_QUOTA = 200;

    public static final Duration QUOTA_WINDOW = Duration.ofHours(1);

    private UploadPolicy() {}

    public static void checkSize(long sizeBytes) {
        if (sizeBytes > MAX_BYTES) {
            throw new FileTooLargeException();
        }
    }

    public static void checkPixels(ImageDimensions dimensions) {
        if (dimensions.pixels() > MAX_PIXELS) {
            throw new ImageTooLargeException();
        }
    }

    /** {@code uploadedInWindow}는 지난 {@link #QUOTA_WINDOW} 동안 올린 수다. */
    public static void checkQuota(long uploadedInWindow) {
        if (uploadedInWindow >= HOURLY_QUOTA) {
            throw new UploadQuotaExceededException();
        }
    }
}
