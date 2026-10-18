package com.openmarket.member.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 배송지(docs/glossary.md "배송지"). {@link Member} 집계 안의 엔티티이고 회원을 거쳐서만 바꾼다. */
@Entity
@Table(name = "address")
public class Address {

    @Id private UUID id;

    // 코드에서 읽지 않지만 Member.addresses(mappedBy)가 외래 키를 이 필드로 쓴다.
    @SuppressWarnings("UnusedVariable")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    @Column private @Nullable String label;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false)
    private String recipientPhone;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Column(name = "base_address", nullable = false)
    private String baseAddress;

    @Column(name = "detail_address")
    private @Nullable String detailAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Address() {}

    Address(UUID id, Member member, Details details, Instant now) {
        this.id = id;
        this.member = member;
        this.createdAt = now;
        apply(details, now);
    }

    void change(Details details, Instant now) {
        apply(details, now);
    }

    private void apply(Details details, Instant now) {
        this.label = details.label();
        this.recipientName = details.recipientName();
        this.recipientPhone = details.recipientPhone();
        this.zipCode = details.zipCode();
        this.baseAddress = details.baseAddress();
        this.detailAddress = details.detailAddress();
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public @Nullable String label() {
        return label;
    }

    public String recipientName() {
        return recipientName;
    }

    public String recipientPhone() {
        return recipientPhone;
    }

    public String zipCode() {
        return zipCode;
    }

    public String baseAddress() {
        return baseAddress;
    }

    public @Nullable String detailAddress() {
        return detailAddress;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "Address[id=" + id + "]";
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Address address && Objects.equals(id, address.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    /**
     * 배송지 내용. 만들 때 검사하고 앞뒤 공백을 지운다. 이름표와 상세 주소는 비워도 된다.
     *
     * @param label 이름표(예: 집, 회사). 20자까지
     * @param recipientName 받는 사람
     * @param recipientPhone 받는 사람 연락처. 하이픈으로 나눈 국내 전화번호
     * @param zipCode 우편번호 5자리
     * @param baseAddress 도로명 또는 지번 주소
     * @param detailAddress 상세 주소(동·호수 등)
     */
    public record Details(
            @Nullable String label,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String baseAddress,
            @Nullable String detailAddress) {

        public Details {
            label = ContactRules.optionalText(label, "배송지 이름표", 20);
            recipientName = ContactRules.name(recipientName);
            recipientPhone = ContactRules.phone(recipientPhone);
            zipCode = ContactRules.zipCode(zipCode);
            baseAddress = ContactRules.requiredText(baseAddress, "기본 주소", 200);
            detailAddress = ContactRules.optionalText(detailAddress, "상세 주소", 200);
        }

        /** 개인정보를 남기지 않는다(NFR-7). */
        @Override
        public String toString() {
            return "Address.Details[***]";
        }
    }
}
