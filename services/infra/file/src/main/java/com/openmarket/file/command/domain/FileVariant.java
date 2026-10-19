package com.openmarket.file.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** 파일의 변형 하나(썸네일). {@link StoredFile}에 딸린 값이다. */
@Embeddable
public class FileVariant {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageVariant variant;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected FileVariant() {}

    FileVariant(
            ImageVariant variant, String storageKey, ImageDimensions dimensions, long sizeBytes) {
        this.variant = variant;
        this.storageKey = storageKey;
        this.width = dimensions.width();
        this.height = dimensions.height();
        this.sizeBytes = sizeBytes;
    }

    public ImageVariant variant() {
        return variant;
    }

    public String storageKey() {
        return storageKey;
    }

    public ImageDimensions dimensions() {
        return new ImageDimensions(width, height);
    }

    public long sizeBytes() {
        return sizeBytes;
    }
}
