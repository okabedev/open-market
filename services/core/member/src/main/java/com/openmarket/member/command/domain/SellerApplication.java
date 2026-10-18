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
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 입점 신청 집계(SEL-1, SEL-2, docs/glossary.md "입점 신청"). 회원당 심사 중({@code SUBMITTED})인 신청은 하나다. 반려되면 새로
 * 신청할 수 있다.
 *
 * <p>심사는 상태가 같으면 다시 해도 바뀌지 않는다(멱등). 이미 다른 결과로 심사한 신청은 바꿀 수 없다.
 */
@Entity
@Table(name = "seller_application")
public class SellerApplication {

    static final int REASON_MAX_LENGTH = 500;

    @Id private UUID id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "business_number", nullable = false)
    private String businessNumber;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Embedded private SettlementAccount settlementAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SellerApplicationStatus status;

    @Column(name = "reject_reason")
    private @Nullable String rejectReason;

    @Column(name = "reviewed_by")
    private @Nullable UUID reviewedBy;

    @Column(name = "reviewed_at")
    private @Nullable Instant reviewedAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Version private long version;

    protected SellerApplication() {}

    private SellerApplication(
            UUID id, UUID memberId, Business business, SettlementAccount account, Instant now) {
        this.id = id;
        this.memberId = memberId;
        this.storeName = business.storeName();
        this.businessNumber = business.businessNumber();
        this.representativeName = business.representativeName();
        this.settlementAccount = account;
        this.status = SellerApplicationStatus.SUBMITTED;
        this.submittedAt = now;
    }

    public static SellerApplication submit(
            UUID id, UUID memberId, Business business, SettlementAccount account, Instant now) {
        return new SellerApplication(id, memberId, business, account, now);
    }

    /** 승인한다. 처음 승인했으면 {@code true}다. 반려된 신청이면 실패한다. */
    public boolean approve(UUID adminId, Instant now) {
        if (status == SellerApplicationStatus.APPROVED) {
            return false;
        }
        if (status != SellerApplicationStatus.SUBMITTED) {
            throw new ApplicationAlreadyReviewedException();
        }
        status = SellerApplicationStatus.APPROVED;
        reviewedBy = adminId;
        reviewedAt = now;
        return true;
    }

    /** 반려한다. 사유가 꼭 있어야 한다(SEL-2). 처음 반려했으면 {@code true}다. 승인된 신청이면 실패한다. */
    public boolean reject(UUID adminId, String reason, Instant now) {
        String checked = ContactRules.requiredText(reason, "반려 사유", REASON_MAX_LENGTH);
        if (status == SellerApplicationStatus.REJECTED) {
            return false;
        }
        if (status != SellerApplicationStatus.SUBMITTED) {
            throw new ApplicationAlreadyReviewedException();
        }
        status = SellerApplicationStatus.REJECTED;
        rejectReason = checked;
        reviewedBy = adminId;
        reviewedAt = now;
        return true;
    }

    /**
     * 신청자가 탈퇴했다(ACC-4). 심사 중이면 {@code CANCELLED}로 닫고, 상태와 상관없이 대표자와 계좌 정보를 가명 처리한다. 상호와 사업자 번호는 공개
     * 정보라 남긴다.
     */
    public void withdrawApplicant(Instant now) {
        if (status == SellerApplicationStatus.SUBMITTED) {
            status = SellerApplicationStatus.CANCELLED;
            reviewedAt = now;
        }
        representativeName = Member.WITHDRAWN_NAME;
        settlementAccount = SettlementAccount.erased();
    }

    public UUID id() {
        return id;
    }

    public UUID memberId() {
        return memberId;
    }

    public Business business() {
        return new Business(storeName, businessNumber, representativeName);
    }

    public SettlementAccount settlementAccount() {
        return settlementAccount;
    }

    public SellerApplicationStatus status() {
        return status;
    }

    public @Nullable String rejectReason() {
        return rejectReason;
    }

    public @Nullable UUID reviewedBy() {
        return reviewedBy;
    }

    /** 개인정보를 남기지 않는다(NFR-7). */
    @Override
    public String toString() {
        return "SellerApplication[id=" + id + ", status=" + status + "]";
    }

    /**
     * 사업자 정보. 만들 때 검사하고 정리한다.
     *
     * @param storeName 상호. 2~30자
     * @param businessNumber 사업자 등록 번호. 하이픈은 있어도 없어도 되고, {@code 123-45-67890} 형식으로 정리한다
     * @param representativeName 대표자 이름
     */
    public record Business(String storeName, String businessNumber, String representativeName) {

        private static final Pattern DIGITS = Pattern.compile("\\d{10}");
        private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

        public Business {
            storeName = ContactRules.requiredText(storeName, "상호", 30);
            if (storeName.codePointCount(0, storeName.length()) < 2) {
                throw new InvalidMemberInputException("상호는 2자 이상입니다.");
            }
            businessNumber = normalizeBusinessNumber(businessNumber);
            representativeName = ContactRules.name(representativeName);
        }

        /** 국세청 사업자 등록 번호 검증 숫자 규칙. */
        private static String normalizeBusinessNumber(@Nullable String value) {
            String digits = value == null ? "" : value.replace("-", "").strip();
            if (!DIGITS.matcher(digits).matches() || !hasValidCheckDigit(digits)) {
                throw new InvalidMemberInputException("사업자 등록 번호가 올바르지 않습니다.");
            }
            return digits.substring(0, 3)
                    + "-"
                    + digits.substring(3, 5)
                    + "-"
                    + digits.substring(5);
        }

        private static boolean hasValidCheckDigit(String digits) {
            int sum = 0;
            for (int i = 0; i < WEIGHTS.length; i++) {
                sum += (digits.charAt(i) - '0') * WEIGHTS[i];
            }
            sum += (digits.charAt(8) - '0') * 5 / 10;
            return (10 - sum % 10) % 10 == digits.charAt(9) - '0';
        }

        /** 개인정보(대표자 이름)를 남기지 않는다(NFR-7). */
        @Override
        public String toString() {
            return "Business[storeName=" + storeName + "]";
        }
    }
}
