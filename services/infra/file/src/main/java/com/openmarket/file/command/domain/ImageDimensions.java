package com.openmarket.file.command.domain;

/** 이미지 크기(픽셀). EXIF 방향을 반영한, 보이는 그대로의 폭과 높이다. */
public record ImageDimensions(int width, int height) {

    public ImageDimensions {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("크기는 양수여야 한다: " + width + "x" + height);
        }
    }

    public long pixels() {
        return (long) width * height;
    }
}
