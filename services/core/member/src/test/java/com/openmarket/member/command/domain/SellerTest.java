package com.openmarket.member.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SellerTest {

    static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");
    static final Instant LATER = NOW.plusSeconds(3600);
    static final UUID ADMIN = UUID.randomUUID();

    static SettlementAccount account() {
        return new SettlementAccount("국민은행", "홍길동", "encrypted", "********6789");
    }

    static SellerApplication application() {
        return SellerApplication.submit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new SellerApplication.Business("길동상회", "1234567891", "홍길동"),
                account(),
                NOW);
    }

    @Nested
    class Application {

        @Test
        @DisplayName("신청하면 심사 중이고 사업자 번호는 하이픈을 넣어 저장한다")
        void submits() {
            SellerApplication application = application();

            assertThat(application.status()).isEqualTo(SellerApplicationStatus.SUBMITTED);
            assertThat(application.business().businessNumber()).isEqualTo("123-45-67891");
        }

        @ParameterizedTest
        @ValueSource(strings = {"123-45-67890", "12345678", "abc-de-fghij", ""})
        @DisplayName("사업자 번호는 10자리이고 검증 숫자가 맞아야 한다")
        void rejectsInvalidBusinessNumber(String number) {
            assertThatThrownBy(() -> new SellerApplication.Business("길동상회", number, "홍길동"))
                    .isInstanceOf(InvalidMemberInputException.class);
        }

        @Test
        @DisplayName("상호는 2~30자다")
        void validatesStoreName() {
            assertThatThrownBy(() -> new SellerApplication.Business("가", "123-45-67891", "홍길동"))
                    .isInstanceOf(InvalidMemberInputException.class);
            assertThatThrownBy(
                            () ->
                                    new SellerApplication.Business(
                                            "가".repeat(31), "123-45-67891", "홍길동"))
                    .isInstanceOf(InvalidMemberInputException.class);
        }

        @Test
        @DisplayName("승인하면 심사자와 시각을 남긴다. 다시 승인해도 그대로이고, 반려된 신청은 승인할 수 없다")
        void approves() {
            SellerApplication application = application();

            assertThat(application.approve(ADMIN, LATER)).isTrue();
            assertThat(application.approve(ADMIN, LATER)).isFalse();
            assertThat(application.status()).isEqualTo(SellerApplicationStatus.APPROVED);
            assertThat(application.reviewedBy()).isEqualTo(ADMIN);

            assertThatThrownBy(() -> application.reject(ADMIN, "사유", LATER))
                    .isInstanceOf(ApplicationAlreadyReviewedException.class);
        }

        @Test
        @DisplayName("반려에는 사유가 꼭 있어야 한다. 다시 반려해도 그대로이고, 반려된 신청은 승인할 수 없다")
        void rejects() {
            SellerApplication application = application();

            assertThatThrownBy(() -> application.reject(ADMIN, " ", LATER))
                    .isInstanceOf(InvalidMemberInputException.class);
            assertThat(application.reject(ADMIN, "사업자 정보가 확인되지 않습니다", LATER)).isTrue();
            assertThat(application.reject(ADMIN, "다른 사유", LATER)).isFalse();
            assertThat(application.rejectReason()).isEqualTo("사업자 정보가 확인되지 않습니다");

            assertThatThrownBy(() -> application.approve(ADMIN, LATER))
                    .isInstanceOf(ApplicationAlreadyReviewedException.class);
        }
    }

    @Nested
    class SellerState {

        @Test
        @DisplayName("승인된 신청으로 판매자를 만든다. ID는 회원 ID이고 처음에는 활동 중이다")
        void opensFromApprovedApplication() {
            SellerApplication application = application();
            application.approve(ADMIN, LATER);

            Seller seller = Seller.open(application, LATER);

            assertThat(seller.id()).isEqualTo(application.memberId());
            assertThat(seller.status()).isEqualTo(SellerStatus.ACTIVE);
            assertThat(seller.business().storeName()).isEqualTo("길동상회");
        }

        @Test
        @DisplayName("승인되지 않은 신청으로는 판매자를 만들 수 없다")
        void requiresApproval() {
            assertThatThrownBy(() -> Seller.open(application(), LATER))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("정지에는 사유가 필요하다. 정지·해제를 반복해도 상태가 같으면 바뀌지 않는다")
        void suspendsAndReinstates() {
            SellerApplication application = application();
            application.approve(ADMIN, LATER);
            Seller seller = Seller.open(application, LATER);

            assertThat(seller.reinstate(LATER)).isFalse();
            assertThatThrownBy(() -> seller.suspend("", LATER))
                    .isInstanceOf(InvalidMemberInputException.class);
            assertThat(seller.suspend("약관 위반", LATER)).isTrue();
            assertThat(seller.suspend("약관 위반", LATER)).isFalse();
            assertThat(seller.status()).isEqualTo(SellerStatus.SUSPENDED);
            assertThat(seller.suspendReason()).isEqualTo("약관 위반");

            assertThat(seller.reinstate(LATER)).isTrue();
            assertThat(seller.status()).isEqualTo(SellerStatus.ACTIVE);
            assertThat(seller.suspendReason()).isNull();
        }
    }

    @Nested
    class Withdrawal {

        @Test
        @DisplayName("신청자가 탈퇴하면 심사 중인 신청은 CANCELED가 되고, 모든 신청의 대표자·계좌 정보를 가명 처리한다")
        void cancelsAndPseudonymizes() {
            SellerApplication pending = application();
            SellerApplication rejected = application();
            rejected.reject(ADMIN, "서류 미비", NOW);

            pending.withdrawApplicant(LATER);
            rejected.withdrawApplicant(LATER);

            assertThat(pending.status()).isEqualTo(SellerApplicationStatus.CANCELED);
            assertThat(rejected.status()).isEqualTo(SellerApplicationStatus.REJECTED);
            for (SellerApplication application : java.util.List.of(pending, rejected)) {
                assertThat(application.business().representativeName())
                        .isEqualTo(Member.WITHDRAWN_NAME);
                assertThat(application.settlementAccount().accountHolder())
                        .isEqualTo(Member.WITHDRAWN_NAME);
                assertThat(application.settlementAccount().accountNumberEncrypted()).isEmpty();
                assertThat(application.settlementAccount().accountNumberMasked()).isEqualTo("****");
            }
        }

        @Test
        @DisplayName("취소된 신청은 승인할 수 없다")
        void canceledCannotBeApproved() {
            SellerApplication application = application();
            application.withdrawApplicant(LATER);

            assertThatThrownBy(() -> application.approve(ADMIN, LATER))
                    .isInstanceOf(ApplicationAlreadyReviewedException.class);
        }
    }

    @Test
    @DisplayName("정산 계좌 번호는 숫자 10~16자리이고 하이픈은 지운다")
    void normalizesAccountNumber() {
        assertThat(SettlementAccount.normalizeNumber("110-123-456789")).isEqualTo("110123456789");
        assertThatThrownBy(() -> SettlementAccount.normalizeNumber("12345"))
                .isInstanceOf(InvalidMemberInputException.class);
        assertThatThrownBy(() -> SettlementAccount.normalizeNumber("110-abc-456789"))
                .isInstanceOf(InvalidMemberInputException.class);
    }
}
