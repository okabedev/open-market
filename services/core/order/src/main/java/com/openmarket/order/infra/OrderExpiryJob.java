package com.openmarket.order.infra;

import com.openmarket.order.command.application.CancelOrderCommand;
import com.openmarket.order.command.application.CancelOrderUseCase;
import com.openmarket.order.command.domain.OrderRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 기한이 지난 결제 전 주문을 {@code PAYMENT_TIMEOUT}으로 취소한다(docs/architecture/order.md "만료와 결제 전 취소").
 * 인스턴스가 여러 개여도 ShedLock으로 한 곳에서만 돈다. 주문마다 트랜잭션을 따로 열고, 한 번에 다 취소하지 못하면 다음 주기에 이어 한다.
 */
@Component
public class OrderExpiryJob {

    static final String LOCK_NAME = "order-expiry";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderRepository orders;
    private final CancelOrderUseCase cancel;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;
    private final Duration paymentTimeout;

    public OrderExpiryJob(
            OrderRepository orders,
            CancelOrderUseCase cancel,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock,
            @Value("${openmarket.order.payment-timeout:15m}") Duration paymentTimeout) {
        this.orders = orders;
        this.cancel = cancel;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
        this.paymentTimeout = paymentTimeout;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.order.expiry-interval:1m}",
            initialDelayString = "${openmarket.order.expiry-interval:1m}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::expire,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** 잠금 없이 한 번 취소한다. 취소한(시도한) 주문 수를 돌려준다. */
    public int expire() {
        List<UUID> expired =
                orders.findUnpaidIdsPlacedBefore(clock.instant().minus(paymentTimeout), BATCH_SIZE);
        expired.forEach(
                id ->
                        cancel.execute(
                                new CancelOrderCommand(
                                        id, CancelOrderCommand.Reason.PAYMENT_TIMEOUT)));
        if (!expired.isEmpty()) {
            log.info("결제 기한이 지난 주문 {}건을 취소했다", expired.size());
        }
        return expired.size();
    }
}
