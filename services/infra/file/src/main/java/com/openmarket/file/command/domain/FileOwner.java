package com.openmarket.file.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 파일 소유자(docs/glossary.md "파일 소유자"). 마지막으로 반영한 소유자 이벤트의 리비전을 둔다. 재처리로 오래된 스냅샷이 다시 와도 첨부를 되돌리지 않는다.
 * 소유자의 판매자는 비공개 파일(클레임 사진)을 볼 수 있다.
 */
@Entity
@Table(name = "file_owner")
public class FileOwner {

    @Id
    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private FileOwnerType ownerType;

    @Column(nullable = false)
    private long revision;

    // 소유자의 판매자(상품의 판매자, 클레임의 판매자). 이 컬럼 전에 만든 상품 소유자는 다음 상품 이벤트에서 채운다.
    @Column(name = "seller_id")
    private @Nullable UUID sellerId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FileOwner() {}

    private FileOwner(
            FileOwnerType ownerType, UUID ownerId, UUID sellerId, long revision, Instant now) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.sellerId = sellerId;
        this.revision = revision;
        this.updatedAt = now;
    }

    /** 처음 받은 소유자 이벤트로 만든다. */
    public static FileOwner create(
            FileOwnerType ownerType, UUID ownerId, UUID sellerId, long revision, Instant now) {
        return new FileOwner(ownerType, ownerId, sellerId, revision, now);
    }

    /**
     * 더 새로운 이벤트면 리비전을 올린다.
     *
     * @return 반영해야 하는 이벤트면 true. 받은 것보다 작거나 같으면 false
     */
    public boolean advance(long revision, UUID sellerId, Instant now) {
        if (revision <= this.revision) {
            return false;
        }
        this.revision = revision;
        this.sellerId = sellerId;
        this.updatedAt = now;
        return true;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public FileOwnerType ownerType() {
        return ownerType;
    }

    public long revision() {
        return revision;
    }

    public @Nullable UUID sellerId() {
        return sellerId;
    }
}
