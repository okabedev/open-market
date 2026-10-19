package com.openmarket.file.command.domain;

import java.util.List;

/** 파일 용도. 저장 디렉터리와 만들 변형이 정해진다. 리뷰·클레임 사진은 그 기능의 마일스톤(M5·M4)에서 더한다. */
// List.of로 만든 불변 목록이다. Error Prone은 List 타입만 보고 가변으로 판단한다.
@SuppressWarnings("ImmutableEnumChecker")
public enum FilePurpose {
    PRODUCT_IMAGE("product-image", List.of(ImageVariant.W300, ImageVariant.W800));

    private final String directory;
    private final List<ImageVariant> variants;

    FilePurpose(String directory, List<ImageVariant> variants) {
        this.directory = directory;
        this.variants = variants;
    }

    public String directory() {
        return directory;
    }

    public List<ImageVariant> variants() {
        return variants;
    }
}
