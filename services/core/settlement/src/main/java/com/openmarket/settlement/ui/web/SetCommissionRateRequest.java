package com.openmarket.settlement.ui.web;

import com.openmarket.settlement.command.application.SetCommissionRateCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * 카테고리 수수료율. 값이 빠지면 0%로 저장되지 않게 필수다.
 *
 * @param rateBp 0~10,000(bp, 0.01%)
 */
public record SetCommissionRateRequest(@NotNull Integer rateBp) {

    SetCommissionRateCommand toCommand(UUID adminId, UUID categoryId) {
        return new SetCommissionRateCommand(adminId, categoryId, rateBp);
    }
}
