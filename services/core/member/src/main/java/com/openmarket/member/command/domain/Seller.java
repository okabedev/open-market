package com.openmarket.member.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 판매자 집계(docs/glossary.md "판매자"). 승인된 입점 신청으로 만든다. {@code id}는 memberId(= sellerId = accountId)다.
 *
 * <p>정지·해제는 상태가 같으면 다시 해도 바뀌지 않는다(멱등). 판매자 역할은 auth가 이벤트를 받아 붙이고 뗀다.
 */
@Entity
@Table(name = "seller")
public class Seller {

    @Id private UUID id;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "business_number", nullable = false)
    private String businessNumber;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Embedded private SettlementAccount settlementAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerStatus status;

    @Column(name = "suspend_reason")
    private @Nullable String suspendReason;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected Seller() {}

    private Seller(SellerApplication application, Instant now) {
        SellerApplication.Business business = application.business();
        this.id = application.memberId();
        this.applicationId = application.id();
        this.storeName = business.storeName();
        this.businessNumber = business.businessNumber();
        this.representativeName = business.representativeName();
        this.settlementAccount = application.settlementAccount().copy();
        this.status = SellerStatus.ACTIVE;
        this.approvedAt = now;
        this.updatedAt = now;
    }

    /** 승인된 신청으로 판매자를 만든다. */
    public static Seller open(SellerApplication application, Instant now) {
        if (application.status() != SellerApplicationStatus.APPROVED) {
            throw new IllegalStateException("승인된 신청만 판매자가 된다: " + application.id());
        }
        return new Seller(application, now);
    }

    /** 정지한다(SEL-3). 사유가 꼭 있어야 한다. 처음 정지했으면 {@code true}다. */
    public boolean suspend(String reason, Instant now) {
        String checked =
                ContactRules.requiredText(reason, "정지 사유", SellerApplication.REASON_MAX_LENGTH);
        if (status == SellerStatus.SUSPENDED) {
            return false;
        }
        status = SellerStatus.SUSPENDED;
        suspendReason = checked;
        updatedAt = now;
        return true;
    }

    /** 정지를 푼다. 처음 풀었으면 {@code true}다. */
    public boolean reinstate(Instant now) {
        if (status == SellerStatus.ACTIVE) {
            return false;
        }
        status = SellerStatus.ACTIVE;
        suspendReason = null;
        updatedAt = now;
        return true;
    }

    public UUID id() {
        return id;
    }

    public SellerApplication.Business business() {
        return new SellerApplication.Business(storeName, businessNumber, representativeName);
    }

    /** 정산 지급(M5)이 쓴다. */
    public SettlementAccount settlementAccount() {
        return settlementAccount;
    }

    public SellerStatus status() {
        return status;
    }

    public @Nullable String suspendReason() {
        return suspendReason;
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "Seller[id=" + id + ", status=" + status + "]";
    }
}
