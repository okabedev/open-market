package com.openmarket.settlement.ui.web;

import com.openmarket.settlement.query.application.SettlementQueryService;
import com.openmarket.settlement.query.dto.CursorPageView;
import com.openmarket.settlement.query.dto.PageView;
import com.openmarket.settlement.query.dto.SettlementItemView;
import com.openmarket.settlement.query.dto.SettlementStatus;
import com.openmarket.settlement.query.dto.SettlementView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 정산 조회. 목록은 쪽 번호, 내역은 커서 목록이다. 없으면 404 {@code SETTLEMENT_NOT_FOUND}. */
@RestController
@RequestMapping("/v1/admin/settlements")
public class AdminSettlementQueryController {

    private final SettlementQueryService settlements;

    public AdminSettlementQueryController(SettlementQueryService settlements) {
        this.settlements = settlements;
    }

    /** 모든 정산. 상태, 판매자로 거른다. 주지 않으면 거르지 않는다. 최근 확정 순. */
    @GetMapping
    public PageView<SettlementView> list(
            @RequestParam(required = false) @Nullable SettlementStatus status,
            @RequestParam(required = false) @Nullable UUID sellerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return settlements.adminSettlements(status, sellerId, page, size);
    }

    @GetMapping("/{settlementId}")
    public SettlementView settlement(@PathVariable UUID settlementId) {
        return settlements.adminSettlement(settlementId);
    }

    /** 정산의 내역(정산 대상). 구매확정 최근 순. */
    @GetMapping("/{settlementId}/items")
    public CursorPageView<SettlementItemView> items(
            @PathVariable UUID settlementId,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return settlements.adminSettlementItems(settlementId, cursor, size);
    }
}
