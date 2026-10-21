package com.openmarket.fulfillment.infra;

import com.openmarket.fulfillment.command.application.DeliverShipmentUseCase;
import com.openmarket.fulfillment.command.application.StartTransitUseCase;
import com.openmarket.fulfillment.command.domain.ShipmentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 택배사 연동 대신 발송한 배송을 배송 중, 배송 완료로 바꾼다(docs/architecture/fulfillment.md "배송 시뮬레이터"). 인스턴스가 여러 개여도
 * ShedLock으로 한 곳에서만 돈다. 두 단계의 대상을 먼저 읽고 바꾸므로 한 주기에 배송 하나가 한 단계만 간다.
 */
@Component
public class ShipmentSimulatorJob {

    static final String LOCK_NAME = "fulfillment-simulator";
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(ShipmentSimulatorJob.class);

    private final ShipmentRepository shipments;
    private final StartTransitUseCase startTransit;
    private final DeliverShipmentUseCase deliver;
    private final LockingTaskExecutor lockingTaskExecutor;
    private final Clock clock;
    private final Duration inTransitAfter;
    private final Duration deliverAfter;

    public ShipmentSimulatorJob(
            ShipmentRepository shipments,
            StartTransitUseCase startTransit,
            DeliverShipmentUseCase deliver,
            LockingTaskExecutor lockingTaskExecutor,
            Clock clock,
            @Value("${openmarket.fulfillment.simulator.in-transit-after:10m}")
                    Duration inTransitAfter,
            @Value("${openmarket.fulfillment.simulator.deliver-after:1h}") Duration deliverAfter) {
        this.shipments = shipments;
        this.startTransit = startTransit;
        this.deliver = deliver;
        this.lockingTaskExecutor = lockingTaskExecutor;
        this.clock = clock;
        this.inTransitAfter = inTransitAfter;
        this.deliverAfter = deliverAfter;
    }

    @Scheduled(
            fixedDelayString = "${openmarket.fulfillment.simulator.interval:1m}",
            initialDelayString = "${openmarket.fulfillment.simulator.interval:1m}")
    public void run() {
        lockingTaskExecutor.executeWithLock(
                (Runnable) this::simulate,
                new LockConfiguration(
                        clock.instant(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
    }

    /** 잠금 없이 한 주기를 돈다. */
    public void simulate() {
        Instant now = clock.instant();
        List<UUID> toDeliver =
                shipments.findInTransitIdsBefore(now.minus(deliverAfter), BATCH_SIZE);
        List<UUID> toTransit =
                shipments.findShippedIdsBefore(now.minus(inTransitAfter), BATCH_SIZE);
        advance(toDeliver, deliver::execute, "배송 완료");
        advance(toTransit, startTransit::execute, "배송 중");
    }

    private static void advance(List<UUID> ids, Consumer<UUID> step, String name) {
        for (UUID id : ids) {
            try {
                step.accept(id);
            } catch (RuntimeException e) {
                // 한 배송의 실패가 나머지를 막지 않게 한다. 다음 주기에 다시 한다.
                log.warn("배송을 {}(으)로 바꾸지 못했다: shipmentId={}", name, id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("배송 {}건을 {}(으)로 바꿨다", ids.size(), name);
        }
    }
}
