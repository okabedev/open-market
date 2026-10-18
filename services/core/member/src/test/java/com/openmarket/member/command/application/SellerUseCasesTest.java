package com.openmarket.member.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.member.command.application.SellerFakes.Applications;
import com.openmarket.member.command.application.SellerFakes.RecordingAuditLog;
import com.openmarket.member.command.application.SellerFakes.RecordingOutbox;
import com.openmarket.member.command.application.SellerFakes.Sellers;
import com.openmarket.member.command.domain.AlreadySellerException;
import com.openmarket.member.command.domain.ApplicationAlreadyReviewedException;
import com.openmarket.member.command.domain.EmailNotVerifiedException;
import com.openmarket.member.command.domain.Member;
import com.openmarket.member.command.domain.SellerApplication;
import com.openmarket.member.command.domain.SellerApplicationPendingException;
import com.openmarket.member.command.domain.SellerApproved;
import com.openmarket.member.command.domain.SellerStatus;
import com.openmarket.member.command.domain.SellerSuspended;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SellerUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-18T03:00:00Z");

    final FakeMemberRepository members = new FakeMemberRepository();
    final Applications applications = new Applications();
    final Sellers sellers = new Sellers();
    final RecordingOutbox outbox = new RecordingOutbox();
    final RecordingAuditLog audit = new RecordingAuditLog();
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final AccountNumberCipherPort cipher = number -> "enc(" + number.length() + ")";
    final UUID memberId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();

    final SubmitSellerApplicationUseCase submit =
            new SubmitSellerApplicationUseCase(members, applications, sellers, cipher, clock);
    final ApproveSellerApplicationUseCase approve =
            new ApproveSellerApplicationUseCase(applications, sellers, outbox, audit, clock);
    final RejectSellerApplicationUseCase reject =
            new RejectSellerApplicationUseCase(applications, outbox, audit, clock);
    final SuspendSellerUseCase suspend = new SuspendSellerUseCase(sellers, outbox, audit, clock);
    final ReinstateSellerUseCase reinstate =
            new ReinstateSellerUseCase(sellers, outbox, audit, clock);

    @BeforeEach
    void verifiedMember() {
        Member member = Member.register(memberId, "buyer@example.com", "홍길동", NOW);
        member.verifyEmail(NOW);
        members.save(member);
    }

    @Test
    @DisplayName("신청하면 계좌 번호는 암호화한 값과 마스킹한 값만 남는다")
    void storesEncryptedAccountNumber() {
        UUID id = submit.execute(command());

        SellerApplication application = applications.rows.get(id);
        assertThat(application.settlementAccount().accountNumberEncrypted()).isEqualTo("enc(12)");
        assertThat(application.settlementAccount().accountNumberMasked()).isEqualTo("********6789");
        assertThat(members.lockedReads).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일 인증 전에는 신청할 수 없다")
    void requiresVerifiedEmail() {
        UUID unverified = UUID.randomUUID();
        members.save(Member.register(unverified, "new@example.com", "김철수", NOW));

        assertThatThrownBy(() -> submit.execute(command(unverified)))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    @DisplayName("심사 중인 신청이 있으면 다시 신청할 수 없고, 반려되면 새로 신청할 수 있다")
    void onePendingApplication() {
        UUID first = submit.execute(command());
        assertThatThrownBy(() -> submit.execute(command()))
                .isInstanceOf(SellerApplicationPendingException.class);

        reject.execute(new ReviewSellerApplicationCommand(adminId, first, "서류 미비"));

        assertThat(submit.execute(command())).isNotEqualTo(first);
    }

    @Test
    @DisplayName("승인하면 판매자가 생기고 SellerApproved와 감사 로그가 한 번만 남는다. 판매자는 다시 신청할 수 없다")
    void approvesOnce() {
        UUID id = submit.execute(command());

        approve.execute(new ReviewSellerApplicationCommand(adminId, id, null));
        approve.execute(new ReviewSellerApplicationCommand(adminId, id, null));

        assertThat(sellers.rows.get(memberId).status()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(outbox.types()).containsExactly(SellerApproved.TYPE);
        assertThat(outbox.events.getFirst().topic()).isEqualTo("member.seller");
        assertThat(outbox.events.getFirst().key()).isEqualTo(memberId.toString());
        assertThat(outbox.events.getFirst().payload())
                .isEqualTo(new SellerApproved(memberId, "길동상회"));
        assertThat(audit.entries).hasSize(1);
        assertThat(audit.entries.getFirst().actorId()).isEqualTo(adminId.toString());
        assertThat(audit.actions()).containsExactly("SELLER_APPLICATION_APPROVED");

        assertThatThrownBy(() -> submit.execute(command()))
                .isInstanceOf(AlreadySellerException.class);
        assertThatThrownBy(
                        () ->
                                reject.execute(
                                        new ReviewSellerApplicationCommand(adminId, id, "늦은 반려")))
                .isInstanceOf(ApplicationAlreadyReviewedException.class);
    }

    @Test
    @DisplayName("반려하면 SellerRejected와 사유가 담긴 감사 로그가 남는다")
    void rejectsWithReason() {
        UUID id = submit.execute(command());

        reject.execute(new ReviewSellerApplicationCommand(adminId, id, "사업자 정보 불일치"));

        assertThat(outbox.types()).containsExactly("SellerRejected");
        assertThat(audit.entries.getFirst().detail()).isEqualTo("사업자 정보 불일치");
        assertThat(sellers.rows).isEmpty();
    }

    @Test
    @DisplayName("정지·해제는 상태가 바뀔 때만 이벤트와 감사 로그를 남긴다")
    void suspendsAndReinstates() {
        UUID id = submit.execute(command());
        approve.execute(new ReviewSellerApplicationCommand(adminId, id, null));
        outbox.events.clear();
        audit.entries.clear();

        suspend.execute(new ChangeSellerStatusCommand(adminId, memberId, "약관 위반"));
        suspend.execute(new ChangeSellerStatusCommand(adminId, memberId, "약관 위반"));
        reinstate.execute(new ChangeSellerStatusCommand(adminId, memberId, null));
        reinstate.execute(new ChangeSellerStatusCommand(adminId, memberId, null));

        assertThat(outbox.types()).containsExactly(SellerSuspended.TYPE, "SellerReinstated");
        assertThat(audit.actions()).containsExactly("SELLER_SUSPENDED", "SELLER_REINSTATED");
    }

    private SubmitSellerApplicationCommand command() {
        return command(memberId);
    }

    private static SubmitSellerApplicationCommand command(UUID memberId) {
        return new SubmitSellerApplicationCommand(
                memberId, "길동상회", "123-45-67891", "홍길동", "국민은행", "홍길동", "110-123-456789");
    }
}
