package com.openmarket.member.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");

    final FakeMemberRepository members = new FakeMemberRepository();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final UUID memberId = UUID.randomUUID();

    @Test
    @DisplayName("가입 이벤트가 두 번 와도 회원은 하나이고 처음 값을 유지한다")
    void registersOnce() {
        RegisterMemberUseCase register = new RegisterMemberUseCase(members, clock);

        register.execute(new RegisterMemberCommand(memberId, "buyer@example.com", "홍길동"));
        register.execute(new RegisterMemberCommand(memberId, "buyer@example.com", "다른이름"));

        assertThat(members.members).hasSize(1);
        assertThat(members.members.get(memberId).name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("이메일 인증을 기록한다. 회원이 없으면 실패해서 다시 시도하게 한다")
    void verifiesEmail() {
        VerifyMemberEmailUseCase verify = new VerifyMemberEmailUseCase(members, clock);

        assertThatThrownBy(() -> verify.execute(new VerifyMemberEmailCommand(memberId)))
                .isInstanceOf(MemberNotFoundException.class);

        registered();
        verify.execute(new VerifyMemberEmailCommand(memberId));
        assertThat(members.members.get(memberId).isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("프로필을 바꾼다")
    void changesProfile() {
        registered();

        new ChangeProfileUseCase(members, clock)
                .execute(new ChangeProfileCommand(memberId, "김철수", "010-1111-2222"));

        assertThat(members.members.get(memberId).phone()).isEqualTo("010-1111-2222");
    }

    @Test
    @DisplayName("배송지 추가·수정·기본 지정·삭제는 회원 행을 잠그고 한다")
    void addressCommandsLockMember() {
        registered();

        UUID first =
                new AddAddressUseCase(members, clock)
                        .execute(
                                new AddAddressCommand(
                                        memberId,
                                        "집",
                                        "홍길동",
                                        "010-1234-5678",
                                        "06236",
                                        "서울특별시 강남구 테헤란로 123",
                                        "4층",
                                        false));
        UUID second =
                new AddAddressUseCase(members, clock)
                        .execute(
                                new AddAddressCommand(
                                        memberId,
                                        "회사",
                                        "홍길동",
                                        "010-1234-5678",
                                        "04524",
                                        "서울특별시 중구 세종대로 110",
                                        null,
                                        false));
        new ChangeAddressUseCase(members, clock)
                .execute(
                        new ChangeAddressCommand(
                                memberId,
                                second,
                                "본사",
                                "홍길동",
                                "010-1234-5678",
                                "04524",
                                "서울특별시 중구 세종대로 110",
                                "7층"));
        new SetDefaultAddressUseCase(members, clock)
                .execute(new SetDefaultAddressCommand(memberId, second));
        new RemoveAddressUseCase(members, clock).execute(new RemoveAddressCommand(memberId, first));

        Member member = members.members.get(memberId);
        assertThat(member.addresses()).hasSize(1);
        assertThat(member.defaultAddressId()).isEqualTo(second);
        assertThat(member.addresses().getFirst().label()).isEqualTo("본사");
        assertThat(members.lockedReads).isEqualTo(5);
    }

    @Test
    @DisplayName("회원이 아직 없으면 프로필·배송지를 바꿀 수 없다")
    void rejectsUnknownMember() {
        assertThatThrownBy(
                        () ->
                                new ChangeProfileUseCase(members, clock)
                                        .execute(new ChangeProfileCommand(memberId, "홍길동", null)))
                .isInstanceOf(MemberNotFoundException.class);
        assertThatThrownBy(
                        () ->
                                new RemoveAddressUseCase(members, clock)
                                        .execute(
                                                new RemoveAddressCommand(
                                                        memberId, UUID.randomUUID())))
                .isInstanceOf(MemberNotFoundException.class);
    }

    private void registered() {
        members.save(Member.register(memberId, "buyer@example.com", "홍길동", NOW));
    }
}
