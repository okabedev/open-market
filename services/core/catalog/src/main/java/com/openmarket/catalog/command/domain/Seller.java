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

    /**
     * 판매자 정지(SEL-3)를 반영한다.
     *
     * @return 상태가 바뀌었으면 true
     */
    public boolean suspend(Instant now) {
        return changeStatus(SellerStatus.SUSPENDED, now);
    }

    /**
     * 정지 해제를 반영한다.
     *
     * @return 상태가 바뀌었으면 true
     */
    public boolean reinstate(Instant now) {
        return changeStatus(SellerStatus.ACTIVE, now);
    }

    public boolean isActive() {
        return status == SellerStatus.ACTIVE;
    }

    private boolean changeStatus(SellerStatus next, Instant now) {
        if (status == next) {
            return false;
        }
        status = next;
        updatedAt = now;
        return true;
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
