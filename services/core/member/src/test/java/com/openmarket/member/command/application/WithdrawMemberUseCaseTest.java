package com.openmarket.member.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.member.command.application.SellerFakes.Applications;
import com.openmarket.member.command.application.SellerFakes.RecordingAuditLog;
import com.openmarket.member.command.application.SellerFakes.RecordingOutbox;
import com.openmarket.member.command.application.SellerFakes.Sellers;
import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.MemberWithdrawn;
import com.openmarket.member.command.domain.OngoingOrdersException;
import com.openmarket.member.command.domain.SellerApplicationStatus;
import com.openmarket.member.command.domain.SellerCannotWithdrawException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WithdrawMemberUseCaseTest {

    static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");

    final FakeMemberRepository members = new FakeMemberRepository();
    final Applications applications = new Applications();
    final Sellers sellers = new Sellers();
    final RecordingOutbox outbox = new RecordingOutbox();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final UUID memberId = UUID.randomUUID();
    boolean ongoingOrders;

    final WithdrawMemberUseCase withdraw =
            new WithdrawMemberUseCase(
                    members, applications, sellers, id -> ongoingOrders, outbox, clock);

    @BeforeEach
    void verifiedMember() {
        Member member = Member.register(memberId, "buyer@example.com", "홍길동", NOW);
        member.verifyEmail(NOW);
        members.save(member);
    }

    @Test
    @DisplayName("탈퇴하면 가명 처리하고 MemberWithdrawn을 한 번만 쌓는다. 다시 탈퇴해도 그대로다")
    void withdrawsOnce() {
        withdraw.execute(new WithdrawMemberCommand(memberId));
        withdraw.execute(new WithdrawMemberCommand(memberId));

        assertThat(members.members.get(memberId).isWithdrawn()).isTrue();
        assertThat(outbox.types()).containsExactly(MemberWithdrawn.TYPE);
        assertThat(outbox.events.getFirst().topic()).isEqualTo("member.member");
        assertThat(outbox.events.getFirst().key()).isEqualTo(memberId.toString());
        assertThat(outbox.events.getFirst().payload()).isEqualTo(new MemberWithdrawn(memberId));
    }

    @Test
    @DisplayName("심사 중인 입점 신청은 탈퇴와 함께 닫힌다")
    void cancelsPendingApplication() {
        UUID applicationId =
                new SubmitSellerApplicationUseCase(
                                members, applications, sellers, number -> "enc", clock)
                        .execute(
                                new SubmitSellerApplicationCommand(
                                        memberId,
                                        "길동상회",
                                        "123-45-67891",
                                        "홍길동",
                                        "국민은행",
                                        "홍길동",
                                        "110-123-456789"));

        withdraw.execute(new WithdrawMemberCommand(memberId));

        assertThat(applications.rows.get(applicationId).status())
                .isEqualTo(SellerApplicationStatus.CANCELLED);
    }

    @Test
    @DisplayName("판매자는 탈퇴할 수 없다")
    void sellerCannotWithdraw() {
        UUID applicationId =
                new SubmitSellerApplicationUseCase(
                                members, applications, sellers, number -> "enc", clock)
                        .execute(
                                new SubmitSellerApplicationCommand(
                                        memberId,
                                        "길동상회",
                                        "123-45-67891",
                                        "홍길동",
                                        "국민은행",
                                        "홍길동",
                                        "110-123-456789"));
        new ApproveSellerApplicationUseCase(
                        applications, sellers, outbox, new RecordingAuditLog(), clock)
                .execute(
                        new ReviewSellerApplicationCommand(UUID.randomUUID(), applicationId, null));

        assertThatThrownBy(() -> withdraw.execute(new WithdrawMemberCommand(memberId)))
                .isInstanceOf(SellerCannotWithdrawException.class);
        assertThat(members.members.get(memberId).isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("진행 중인 주문이 있으면 탈퇴할 수 없다")
    void ongoingOrdersBlockWithdrawal() {
        ongoingOrders = true;

        assertThatThrownBy(() -> withdraw.execute(new WithdrawMemberCommand(memberId)))
                .isInstanceOf(OngoingOrdersException.class);
        assertThat(outbox.events).isEmpty();
    }
}
