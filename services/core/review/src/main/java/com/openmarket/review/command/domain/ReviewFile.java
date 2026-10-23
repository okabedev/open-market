package com.openmarket.review.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** 리뷰 사진 하나. file 서비스의 파일 ID와 작성할 때 넣은 순서다. 사진은 file이 {@code ReviewCreated}를 받아 리뷰에 붙인다. */
@Embeddable
public class ReviewFile {

    // 0부터 시작하는 순서
    @Column(nullable = false)
    private int position;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    protected ReviewFile() {}

    ReviewFile(int position, UUID fileId) {
        this.position = position;
        this.fileId = fileId;
    }

    public int position() {
        return position;
    }

    public UUID fileId() {
        return fileId;
    }
}
