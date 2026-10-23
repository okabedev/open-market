package com.openmarket.file.command.domain;

import java.util.List;

/** 파일 용도. 저장 디렉터리, 만들 변형, 공개 여부가 정해진다(docs/architecture/file.md). 비공개 파일은 공개 경로로 주지 않는다. */
// List.of로 만든 불변 목록이다. Error Prone은 List 타입만 보고 가변으로 판단한다.
@SuppressWarnings("ImmutableEnumChecker")
public enum FilePurpose {
    PRODUCT_IMAGE("product-image", List.of(ImageVariant.W300, ImageVariant.W800), true),
    /** 클레임 사진. 구매자 본인, 해당 판매자, 관리자만 본다. */
    CLAIM_IMAGE("claim-image", List.of(ImageVariant.W300, ImageVariant.W800), false),
    /** 리뷰 사진. 상품 리뷰 목록에 보이므로 공개다. */
    REVIEW_IMAGE("review-image", List.of(ImageVariant.W300, ImageVariant.W800), true);

    private final String directory;
    private final List<ImageVariant> variants;
    private final boolean publicAccess;

    FilePurpose(String directory, List<ImageVariant> variants, boolean publicAccess) {
        this.directory = directory;
        this.variants = variants;
        this.publicAccess = publicAccess;
    }

    /** 토큰 없이 공개 경로({@code /files/**})로 주는가. */
    public boolean isPublic() {
        return publicAccess;
    }

    public String directory() {
        return directory;
    }

    public List<ImageVariant> variants() {
        return variants;
    }
}
