package com.openmarket.settlement.command.application;

import com.openmarket.settlement.command.domain.CommissionRate;
import com.openmarket.settlement.command.domain.CommissionRateRepository;
import com.openmarket.settlement.command.domain.SettlementItem;
import com.openmarket.settlement.command.domain.SettlementItemRepository;
import com.openmarket.settlement.command.domain.SettlementPeriod;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매확정한 하위 주문으로 정산 대상을 만든다(STL-1, docs/architecture/settlement.md "정산 대상"). 하위 주문마다 한 번만 만든다(이미 있으면
 * 넘기고, DB 유니크가 마지막 방어선이다). 항목마다 지금 요율을 고정하고, 정산 기간은 구매확정 시각으로 정한다.
 */
@Service
public class CreateSettlementItemUseCase {

    private final SettlementItemRepository items;
    private final CommissionRateRepository rates;
    private final Clock clock;
    private final Instant periodAnchor;
    private final Duration periodLength;
    private final int defaultRateBp;

    public CreateSettlementItemUseCase(
            SettlementItemRepository items,
            CommissionRateRepository rates,
            Clock clock,
            @Value("${openmarket.settlement.period.anchor:2026-01-05T00:00:00+09:00}")
                    String periodAnchor,
            @Value("${openmarket.settlement.period.length:7d}") Duration periodLength,
            @Value("${openmarket.settlement.default-commission-rate:1000}") int defaultRateBp) {
        this.items = items;
        this.rates = rates;
        this.clock = clock;
        this.periodAnchor = OffsetDateTime.parse(periodAnchor).toInstant();
        this.periodLength = periodLength;
        this.defaultRateBp = CommissionRate.checked(defaultRateBp);
    }

    @Transactional
    public void execute(CreateSettlementItemCommand command) {
        if (items.existsBySellerOrderId(command.sellerOrderId())) {
            return;
        }
        Map<UUID, Integer> rateByCategory =
                rates
                        .findAllById(
                                command.lines().stream()
                                        .map(CreateSettlementItemCommand.Line::categoryId)
                                        .distinct()
                                        .toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        CommissionRate::categoryId, CommissionRate::rateBp));
        SettlementItem.Purchase purchase =
                new SettlementItem.Purchase(
                        command.orderId(),
                        command.sellerOrderId(),
                        command.sellerId(),
                        command.confirmedAt(),
                        command.sellerCouponDiscount(),
                        command.lines().stream()
                                .map(
                                        line ->
                                                new SettlementItem.Line(
                                                        line.orderLineId(),
                                                        line.productId(),
                                                        line.productName(),
                                                        line.categoryId(),
                                                        line.quantity(),
                                                        line.amount()))
                                .toList());
        items.save(
                SettlementItem.create(
                        purchase,
                        category -> rateByCategory.getOrDefault(category, defaultRateBp),
                        SettlementPeriod.of(command.confirmedAt(), periodAnchor, periodLength),
                        clock.instant()));
    }
}
