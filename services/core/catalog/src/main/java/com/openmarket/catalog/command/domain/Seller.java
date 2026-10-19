package com.openmarket.catalog.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * 판매자 사본(docs/glossary.md "판매자 사본"). member의 {@code SellerApproved}로 만든다. 상품 스냅샷과 상세에 상호를 넣고, 정지된
 * 판매자의 판매를 막는 데 쓴다. ID는 member의 sellerId(= memberId)다.
 */
@Entity
@Table(name = "seller")
public class Seller {

    @Id private UUID id;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Seller() {}

    private Seller(UUID id, String storeName, Instant now) {
        this.id = id;
        this.storeName = storeName;
        this.status = SellerStatus.ACTIVE;
        this.updatedAt = now;
    }

    /** 입점 승인으로 사본을 만든다. */
    public static Seller approve(UUID id, String storeName, Instant now) {
        return new Seller(id, storeName, now);
    }

    public UUID id() {
        return id;
    }

    public String storeName() {
        return storeName;
    }

    public SellerStatus status() {
        return status;
    }
}
