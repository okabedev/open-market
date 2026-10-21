package com.openmarket.payment.command.application;

import com.openmarket.payment.command.application.ApplyGatewayResultUseCase.Applied;
import com.openmarket.payment.command.application.PaymentGatewayPort.Rejected;
import com.openmarket.payment.command.application.PaymentGatewayPort.Unknown;
import com.openmarket.payment.command.domain.Payment;
import com.openmarket.payment.command.domain.PaymentLedgerEntry;
import com.openmarket.payment.command.domain.PaymentNotFoundException;
import com.openmarket.payment.command.domain.PaymentRejectedException;
import com.openmarket.payment.command.domain.PaymentRepository;
import com.openmarket.payment.command.domain.PaymentStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 결제를 승인한다(PAY-1, docs/architecture/payment.md "승인"). 결제를 잠그고 금액을 대조해 {@code IN_PROGRESS}로 커밋한 뒤,
 * 트랜잭션 밖에서 토스를 부른다. 응답으로 정하지 못하면 조회로 정하고, 그래도 안 되면 {@code IN_PROGRESS}로 두어 재조회 작업에 맡긴다.
 */
@Service
public class ConfirmPaymentUseCase {

    private final PaymentRepository payments;
    private final PaymentGatewayPort gateway;
    private final ApplyGatewayResultUseCase apply;
    private final TransactionOperations transaction;
    private final Clock clock;

    public ConfirmPaymentUseCase(
            PaymentRepository payments,
            PaymentGatewayPort gateway,
            ApplyGatewayResultUseCase apply,
            TransactionOperations transaction,
            Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.apply = apply;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * 승인 결과. 정해지지 않았으면 {@code IN_PROGRESS}다.
     *
     * @throws PaymentRejectedException 토스가 거절했다. 결제는 {@code FAILED}다
     */
    public Result execute(ConfirmPaymentCommand command) {
        Requested requested = Objects.requireNonNull(transaction.execute(_ -> request(command)));
        if (requested.request() != Payment.ConfirmRequest.STARTED) {
            return requested.result();
        }
        PaymentGatewayPort.Result response =
                gateway.confirm(
                        command.paymentKey(),
                        command.orderId(),
                        command.amount(),
                        requested.idempotencyKey());
        Applied applied =
                apply.execute(
                        requested.paymentId(),
                        requested.attempt(),
                        PaymentLedgerEntry.Kind.CONFIRM,
                        command.paymentKey(),
                        response);
        if (response instanceof Unknown) {
            response = gateway.find(command.paymentKey());
            applied =
                    apply.execute(
                            requested.paymentId(),
                            requested.attempt(),
                            PaymentLedgerEntry.Kind.QUERY,
                            command.paymentKey(),
                            response);
        }
        if (applied.status() == PaymentStatus.FAILED) {
            throw response instanceof Rejected rejected
                    ? new PaymentRejectedException(rejected.code(), rejected.message())
                    : new PaymentRejectedException(
                            Objects.requireNonNullElse(applied.failureCode(), "UNKNOWN"),
                            "결제가 완료되지 않았습니다.");
        }
        return result(requested, applied.status(), applied.method());
    }

    private Requested request(ConfirmPaymentCommand command) {
        Payment payment =
                payments.findForUpdateByOrderId(command.orderId())
                        .filter(found -> found.isOwnedBy(command.buyerId()))
                        .orElseThrow(PaymentNotFoundException::new);
        Payment.ConfirmRequest request =
                payment.requestConfirm(command.paymentKey(), command.amount(), clock.instant());
        if (request == Payment.ConfirmRequest.STARTED) {
            payments.save(payment);
        }
        return new Requested(
                request,
                payment.id(),
                payment.orderId(),
                payment.amount(),
                payment.confirmAttempt(),
                payment.gatewayIdempotencyKey(),
                payment.method());
    }

    private static Result result(
            Requested requested, PaymentStatus status, @Nullable String method) {
        return new Result(
                requested.paymentId(),
                requested.orderId(),
                status == PaymentStatus.APPROVED ? Status.APPROVED : Status.IN_PROGRESS,
                requested.amount(),
                method);
    }

    /** 승인 요청을 커밋한 뒤의 결제. */
    private record Requested(
            Payment.ConfirmRequest request,
            UUID paymentId,
            UUID orderId,
            long amount,
            int attempt,
            String idempotencyKey,
            @Nullable String method) {

        Result result() {
            return new Result(
                    paymentId,
                    orderId,
                    request == Payment.ConfirmRequest.APPROVED
                            ? Status.APPROVED
                            : Status.IN_PROGRESS,
                    amount,
                    method);
        }
    }

    /**
     * 승인 결과.
     *
     * @param amount 결제 금액(원)
     * @param method 토스 결제수단 이름. 승인됐을 때만 있다
     */
    public record Result(
            UUID paymentId, UUID orderId, Status status, long amount, @Nullable String method) {}

    /** 승인 결과의 상태. */
    public enum Status {
        /** 승인됐다. */
        APPROVED,
        /** 결과를 아직 확인하지 못했다. 재조회 작업이 정한다. */
        IN_PROGRESS
    }
}
