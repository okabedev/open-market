package com.openmarket.settlement.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.settlement.query.dao.SettlementQueryDao;
import com.openmarket.settlement.query.dto.CursorPageView;
import com.openmarket.settlement.query.dto.PageView;
import com.openmarket.settlement.query.dto.PendingSettlementSummaryView;
import com.openmarket.settlement.query.dto.SettlementItemView;
import com.openmarket.settlement.query.dto.SettlementStatus;
import com.openmarket.settlement.query.dto.SettlementView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 조회(판매자, 관리자). 판매자에게 다른 판매자의 정산은 없는 정산이다(404 {@code SETTLEMENT_NOT_FOUND}). 커서가 틀리면 400 {@code
 * SETTLEMENT_INVALID_CURSOR}.
 */
@Service
@Transactional(readOnly = true)
public class SettlementQueryService {

    private final SettlementQueryDao settlements;

    public SettlementQueryService(SettlementQueryDao settlements) {
        this.settlements = settlements;
    }

    public CursorPageView<SettlementView> sellerSettlements(
            UUID sellerId, @Nullable String cursor, int size) {
        return settlements.findSellerSettlements(sellerId, cursor, size);
    }

    public SettlementView sellerSettlement(UUID sellerId, UUID settlementId) {
        SettlementView view = adminSettlement(settlementId);
        if (!view.sellerId().equals(sellerId)) {
            throw notFound();
        }
        return view;
    }

    public CursorPageView<SettlementItemView> sellerSettlementItems(
            UUID sellerId, UUID settlementId, @Nullable String cursor, int size) {
        sellerSettlement(sellerId, settlementId);
        return settlements.findSettlementItems(settlementId, cursor, size);
    }

    public CursorPageView<SettlementItemView> pendingItems(
            UUID sellerId, @Nullable String cursor, int size) {
        return settlements.findPendingItems(sellerId, cursor, size);
    }

    public PendingSettlementSummaryView pendingSummary(UUID sellerId) {
        return settlements.pendingSummary(sellerId);
    }

    public PageView<SettlementView> adminSettlements(
            @Nullable SettlementStatus status, @Nullable UUID sellerId, int page, int size) {
        return settlements.findAdminPage(status, sellerId, page, size);
    }

    public SettlementView adminSettlement(UUID settlementId) {
        return settlements
                .findSettlement(settlementId)
                .orElseThrow(SettlementQueryService::notFound);
    }

    public CursorPageView<SettlementItemView> adminSettlementItems(
            UUID settlementId, @Nullable String cursor, int size) {
        adminSettlement(settlementId);
        return settlements.findSettlementItems(settlementId, cursor, size);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("SETTLEMENT_NOT_FOUND", "정산을 찾을 수 없습니다.");
    }
}
