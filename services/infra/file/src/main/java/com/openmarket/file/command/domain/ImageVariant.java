package com.openmarket.file.command.domain;

/** 공개로 제공하는 이미지 변형. 폭을 맞추고 높이는 비율대로 둔다. 원본은 제공하지 않는다. */
public enum ImageVariant {
    W300(300),
    W800(800);

    /** 변형 파일 형식. EXIF(GPS 등)를 남기지 않으려고 모든 변형을 다시 인코딩한다. */
    public static final ImageFormat FORMAT = ImageFormat.JPEG;

    private final int width;

    ImageVariant(int width) {
        this.width = width;
    }

    /** URL과 저장 경로에 쓰는 이름. 예: {@code w300}. */
    public String key() {
        return "w" + width;
    }

    public int width() {
        return width;
    }

    /** 원본 폭이 이 변형보다 작으면 늘리지 않고 원본 폭을 쓴다. */
    public int targetWidth(int sourceWidth) {
        return Math.min(width, sourceWidth);
    }
}
