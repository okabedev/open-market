package com.openmarket.file.command.domain;

import java.util.Arrays;
import java.util.Optional;

/** 받는 이미지 형식. 확장자나 요청의 Content-Type이 아니라 내용의 첫 바이트(매직 바이트)로 판별한다. */
public enum ImageFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_TAG = {'W', 'E', 'B', 'P'};

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    /** 내용의 첫 바이트로 형식을 판별한다. 받지 않는 형식이면 비어 있다. */
    public static Optional<ImageFormat> detect(byte[] content) {
        if (startsWith(content, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_TAG)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    private static boolean startsWith(byte[] content, int offset, byte[] prefix) {
        return content.length >= offset + prefix.length
                && Arrays.equals(content, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
