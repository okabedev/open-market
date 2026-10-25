package com.openmarket.settlement.ui.web;

import com.openmarket.settlement.query.application.SettlementQueryService;
import com.openmarket.settlement.query.dto.CursorPageView;
import com.openmarket.settlement.query.dto.PendingSettlementSummaryView;
import com.openmarket.settlement.query.dto.SettlementItemView;
import com.openmarket.settlement.query.dto.SettlementView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 정산 조회(STL-2). 주체는 토큰의 {@code sub}(= 판매자 ID)다. 목록은 커서 목록이고, 커서가 틀리면 400 {@code
 * SETTLEMENT_INVALID_CURSOR}. 다른 판매자의 정산은 404 {@code SETTLEMENT_NOT_FOUND}.
 */
@RestController
@RequestMapping("/v1/seller")
public class SellerSettlementQueryController {

    private final SettlementQueryService settlements;

    public SellerSettlementQueryController(SettlementQueryService settlements) {
        this.settlements = settlements;
    }

    /** 확정된 정산. 기간 최근 순. */
    @GetMapping("/settlements")
    public CursorPageView<SettlementView> settlements(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return settlements.sellerSettlements(sellerId(principal), cursor, size);
    }

    @GetMapping("/settlements/{settlementId}")
    public SettlementView settlement(Principal principal, @PathVariable UUID settlementId) {
        return settlements.sellerSettlement(sellerId(principal), settlementId);
    }

    /** 정산의 내역(정산 대상). 구매확정 최근 순. */
    @GetMapping("/settlements/{settlementId}/items")
    public CursorPageView<SettlementItemView> settlementItems(
            Principal principal,
            @PathVariable UUID settlementId,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return settlements.sellerSettlementItems(sellerId(principal), settlementId, cursor, size);
    }

    /** 아직 확정되지 않은 정산 대상(정산 예정 내역). 구매확정 최근 순. */
    @GetMapping("/settlement-items/pending")
    public CursorPageView<SettlementItemView> pendingItems(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return settlements.pendingItems(sellerId(principal), cursor, size);
    }

    /** 정산 예정 금액(확정되지 않은 대상의 합계). */
    @GetMapping("/settlement-items/pending/summary")
    public PendingSettlementSummaryView pendingSummary(Principal principal) {
        return settlements.pendingSummary(sellerId(principal));
    }

    private static UUID sellerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
