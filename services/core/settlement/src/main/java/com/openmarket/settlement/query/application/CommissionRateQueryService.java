package com.openmarket.settlement.query.application;

import com.openmarket.settlement.query.dao.CommissionRateQueryDao;
import com.openmarket.settlement.query.dto.CommissionRatesView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수수료율 조회(관리자). */
@Service
@Transactional(readOnly = true)
public class CommissionRateQueryService {

    private final CommissionRateQueryDao rates;
    private final int defaultRateBp;

    public CommissionRateQueryService(
            CommissionRateQueryDao rates,
            @Value("${openmarket.settlement.default-commission-rate:1000}") int defaultRateBp) {
        this.rates = rates;
        this.defaultRateBp = defaultRateBp;
    }

    public CommissionRatesView rates() {
        return new CommissionRatesView(defaultRateBp, rates.findAll());
    }
}
