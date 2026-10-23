package com.openmarket.file.command.domain;

/** 파일이 붙는 대상의 종류(docs/glossary.md "파일 소유자"). */
public enum FileOwnerType {
    /** 상품. 상품 이미지만 붙는다. */
    PRODUCT(FilePurpose.PRODUCT_IMAGE),
    /** 클레임. 클레임 사진만 붙는다. */
    CLAIM(FilePurpose.CLAIM_IMAGE),
    /** 리뷰. 리뷰 사진만 붙는다. */
    REVIEW(FilePurpose.REVIEW_IMAGE);

    private final FilePurpose purpose;

    FileOwnerType(FilePurpose purpose) {
        this.purpose = purpose;
    }

    /** 이 소유자에 붙일 수 있는 파일 용도. */
    public FilePurpose purpose() {
        return purpose;
    }
}
