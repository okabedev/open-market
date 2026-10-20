package com.openmarket.file.command.domain;

/** 파일이 붙는 대상의 종류(docs/glossary.md "파일 소유자"). 리뷰·클레임은 M5·M4에서 더한다. */
public enum FileOwnerType {
    /** 상품. 상품 이미지만 붙는다. */
    PRODUCT(FilePurpose.PRODUCT_IMAGE);

    private final FilePurpose purpose;

    FileOwnerType(FilePurpose purpose) {
        this.purpose = purpose;
    }

    /** 이 소유자에 붙일 수 있는 파일 용도. */
    public FilePurpose purpose() {
        return purpose;
    }
}
