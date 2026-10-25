package com.openmarket.settlement.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * 수수료율(docs/glossary.md "수수료율"). 소분류 카테고리마다 하나이고 ID는 카테고리 ID다. 요율은 bp(0.01%) 단위 정수 0~10,000이다. 요율이
 * 없는 카테고리는 기본 요율이다(docs/architecture/settlement.md "수수료율").
 */
@Entity
@Table(name = "commission_rate")
public class CommissionRate {

    public static final int MIN_BP = 0;
    public static final int MAX_BP = 10_000;

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "rate_bp", nullable = false)
    private int rateBp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version private long version;

    protected CommissionRate() {}

    private CommissionRate(UUID categoryId, int rateBp, Instant now) {
        this.categoryId = categoryId;
        this.rateBp = rateBp;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 카테고리의 요율을 처음 정한다. 범위 밖이면 400 {@code SETTLEMENT_INVALID_INPUT}. */
    public static CommissionRate create(UUID categoryId, int rateBp, Instant now) {
        return new CommissionRate(categoryId, checked(rateBp), now);
    }

    /** 요율을 바꾼다. 같은 요율이면 false이고 아무것도 바꾸지 않는다. */
    public boolean change(int rateBp, Instant now) {
        if (checked(rateBp) == this.rateBp) {
            return false;
        }
        this.rateBp = rateBp;
        this.updatedAt = now;
        return true;
    }

    /** 범위 안의 요율인지 확인한다. 기본 요율 설정도 같은 범위다. */
    public static int checked(int rateBp) {
        if (rateBp < MIN_BP || rateBp > MAX_BP) {
            throw new InvalidSettlementInputException("수수료율은 0~10,000bp입니다.");
        }
        return rateBp;
    }

    /** 항목 금액의 수수료. 원 단위 아래는 버린다. */
    public static long commission(long amount, int rateBp) {
        return Math.floorDiv(Math.multiplyExact(amount, (long) rateBp), (long) MAX_BP);
    }

    public UUID categoryId() {
        return categoryId;
    }

    public int rateBp() {
        return rateBp;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
