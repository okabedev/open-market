package com.openmarket.member.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberTest {

    static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");
    static final Instant LATER = NOW.plusSeconds(60);

    static Member member() {
        return Member.register(UUID.randomUUID(), "buyer@example.com", "홍길동", NOW);
    }

    static Address.Details details(String label) {
        return new Address.Details(
                label, "홍길동", "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 123", "4층");
    }

    @Nested
    class Profile {

        @Test
        @DisplayName("가입 이벤트로 만든 회원은 이메일 인증 전이고 연락처와 배송지가 없다")
        void registeredMemberStartsUnverified() {
            Member member = member();

            assertThat(member.name()).isEqualTo("홍길동");
            assertThat(member.phone()).isNull();
            assertThat(member.isEmailVerified()).isFalse();
            assertThat(member.addresses()).isEmpty();
            assertThat(member.defaultAddressId()).isNull();
        }

        @Test
        @DisplayName("이메일 인증은 처음 한 번만 바뀐다")
        void verifiesEmailOnce() {
            Member member = member();

            assertThat(member.verifyEmail(NOW)).isTrue();
            assertThat(member.verifyEmail(LATER)).isFalse();
            assertThat(member.isEmailVerified()).isTrue();
        }

        @Test
        @DisplayName("이름과 연락처를 바꾼다. 연락처는 지울 수 있다")
        void changesProfile() {
            Member member = member();

            member.changeProfile(" 김철수 ", "010-9876-5432", LATER);
            assertThat(member.name()).isEqualTo("김철수");
            assertThat(member.phone()).isEqualTo("010-9876-5432");

            member.changeProfile("김철수", null, LATER);
            assertThat(member.phone()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("이름은 비울 수 없다")
        void rejectsBlankName(String name) {
            assertInvalid(() -> member().changeProfile(name, null, LATER));
        }

        @Test
        @DisplayName("이름은 50자를 넘을 수 없다")
        void rejectsLongName() {
            assertInvalid(() -> member().changeProfile("가".repeat(51), null, LATER));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {"01012345678", "010-1234-567", "1588-1234", "010-12345-6789", "abc"})
        @DisplayName("연락처는 하이픈으로 나눈 국내 전화번호여야 한다")
        void rejectsMalformedPhone(String phone) {
            assertInvalid(() -> member().changeProfile("홍길동", phone, LATER));
        }

        @ParameterizedTest
        @ValueSource(strings = {"010-1234-5678", "02-123-4567", "031-1234-5678"})
        @DisplayName("휴대폰과 지역 번호를 받는다")
        void acceptsPhones(String phone) {
            Member member = member();
            member.changeProfile("홍길동", phone, LATER);
            assertThat(member.phone()).isEqualTo(phone);
        }
    }

    @Nested
    class Addresses {

        @Test
        @DisplayName("첫 배송지는 기본 배송지가 된다")
        void firstAddressBecomesDefault() {
            Member member = member();

            Address address = member.addAddress(UUID.randomUUID(), details("집"), false, NOW);

            assertThat(member.defaultAddressId()).isEqualTo(address.id());
            assertThat(member.addresses()).containsExactly(address);
        }

        @Test
        @DisplayName("기본으로 지정해 추가하면 기본 배송지가 바뀐다. 아니면 그대로다")
        void addsAsDefaultOnlyWhenAsked() {
            Member member = member();
            Address home = member.addAddress(UUID.randomUUID(), details("집"), false, NOW);

            member.addAddress(UUID.randomUUID(), details("회사"), false, NOW);
            assertThat(member.defaultAddressId()).isEqualTo(home.id());

            Address parents = member.addAddress(UUID.randomUUID(), details("본가"), true, NOW);
            assertThat(member.defaultAddressId()).isEqualTo(parents.id());
        }

        @Test
        @DisplayName("배송지는 10개까지다")
        void limitsToTen() {
            Member member = member();
            IntStream.range(0, Member.MAX_ADDRESSES)
                    .forEach(
                            i ->
                                    member.addAddress(
                                            UUID.randomUUID(), details("배송지" + i), false, NOW));

            assertThatThrownBy(
                            () -> member.addAddress(UUID.randomUUID(), details("하나 더"), false, NOW))
                    .isInstanceOf(AddressLimitExceededException.class)
                    .extracting(e -> ((BusinessException) e).getType())
                    .isEqualTo(ErrorType.UNPROCESSABLE);
            assertThat(member.addresses()).hasSize(Member.MAX_ADDRESSES);
        }

        @Test
        @DisplayName("기본 배송지를 다른 배송지로 바꾼다. 같은 배송지를 다시 지정해도 그대로다")
        void changesDefault() {
            Member member = member();
            member.addAddress(UUID.randomUUID(), details("집"), false, NOW);
            Address office = member.addAddress(UUID.randomUUID(), details("회사"), false, NOW);

            member.makeDefaultAddress(office.id(), LATER);
            member.makeDefaultAddress(office.id(), LATER);

            assertThat(member.defaultAddressId()).isEqualTo(office.id());
        }

        @Test
        @DisplayName("배송지 내용을 고친다")
        void changesAddress() {
            Member member = member();
            Address home = member.addAddress(UUID.randomUUID(), details("집"), false, NOW);

            member.changeAddress(home.id(), details("새 집"), LATER);

            assertThat(home.label()).isEqualTo("새 집");
            assertThat(home.updatedAt()).isEqualTo(LATER);
        }

        @Test
        @DisplayName("기본 배송지를 지우면 남은 배송지 중 가장 최근에 추가한 것이 기본이 된다")
        void promotesNewestWhenDefaultRemoved() {
            Member member = member();
            Address home = member.addAddress(UUID.randomUUID(), details("집"), false, NOW);
            member.addAddress(UUID.randomUUID(), details("회사"), false, NOW);
            Address parents = member.addAddress(UUID.randomUUID(), details("본가"), false, LATER);

            member.removeAddress(home.id(), LATER);

            assertThat(member.defaultAddressId()).isEqualTo(parents.id());
            assertThat(member.addresses()).hasSize(2);
        }

        @Test
        @DisplayName("마지막 배송지를 지우면 기본 배송지가 없다. 없는 배송지를 지워도 그대로다")
        void removesLastAddress() {
            Member member = member();
            Address home = member.addAddress(UUID.randomUUID(), details("집"), false, NOW);

            member.removeAddress(home.id(), LATER);
            member.removeAddress(home.id(), LATER);

            assertThat(member.addresses()).isEmpty();
            assertThat(member.defaultAddressId()).isNull();
        }

        @Test
        @DisplayName("다른 회원의 배송지나 없는 배송지를 고치거나 기본으로 지정하면 찾을 수 없다")
        void rejectsUnknownAddress() {
            Member member = member();
            UUID unknown = UUID.randomUUID();

            assertThatThrownBy(() -> member.changeAddress(unknown, details("집"), LATER))
                    .isInstanceOf(AddressNotFoundException.class);
            assertThatThrownBy(() -> member.makeDefaultAddress(unknown, LATER))
                    .isInstanceOf(AddressNotFoundException.class);
        }

        @Test
        @DisplayName("받는 사람, 연락처, 우편번호, 기본 주소는 꼭 있어야 하고 형식이 맞아야 한다")
        void validatesDetails() {
            assertInvalid(
                    () -> new Address.Details("집", " ", "010-1234-5678", "06236", "서울", null));
            assertInvalid(() -> new Address.Details("집", "홍길동", "0101234", "06236", "서울", null));
            assertInvalid(
                    () -> new Address.Details("집", "홍길동", "010-1234-5678", "6236", "서울", null));
            assertInvalid(
                    () -> new Address.Details("집", "홍길동", "010-1234-5678", "06236", " ", null));
            assertInvalid(
                    () ->
                            new Address.Details(
                                    "가".repeat(21), "홍길동", "010-1234-5678", "06236", "서울", null));
        }

        @Test
        @DisplayName("이름표와 상세 주소는 비워도 되고, 비우면 없는 값으로 둔다")
        void optionalFields() {
            Address.Details details =
                    new Address.Details(" ", "홍길동", "010-1234-5678", "06236", "서울특별시 강남구", "");

            assertThat(details.label()).isNull();
            assertThat(details.detailAddress()).isNull();
        }
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(InvalidMemberInputException.class)
                .extracting(e -> ((BusinessException) e).getType())
                .isEqualTo(ErrorType.INVALID);
    }
}
