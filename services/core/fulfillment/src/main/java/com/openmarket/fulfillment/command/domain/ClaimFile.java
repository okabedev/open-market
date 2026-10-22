package com.openmarket.fulfillment.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** 클레임 사진 하나. file 서비스의 파일 ID와 신청에 넣은 순서다. 사진은 file이 {@code ClaimRequested}를 받아 클레임에 붙인다. */
@Embeddable
public class ClaimFile {

    // 0부터 시작하는 순서
    @Column(nullable = false)
    private int position;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    protected ClaimFile() {}

    ClaimFile(int position, UUID fileId) {
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
