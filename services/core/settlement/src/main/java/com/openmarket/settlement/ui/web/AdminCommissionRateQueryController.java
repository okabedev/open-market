package com.openmarket.settlement.ui.web;

import com.openmarket.settlement.query.application.CommissionRateQueryService;
import com.openmarket.settlement.query.dto.CommissionRatesView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 수수료율 조회. */
@RestController
@RequestMapping("/v1/admin/commission-rates")
public class AdminCommissionRateQueryController {

    private final CommissionRateQueryService rates;

    public AdminCommissionRateQueryController(CommissionRateQueryService rates) {
        this.rates = rates;
    }

    /** 기본 요율과 요율을 정한 카테고리. 카테고리 이름은 화면이 catalog 카테고리 트리로 붙인다. */
    @GetMapping
    public CommissionRatesView list() {
        return rates.rates();
    }
}
