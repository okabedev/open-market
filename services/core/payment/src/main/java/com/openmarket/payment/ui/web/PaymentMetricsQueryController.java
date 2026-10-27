package com.openmarket.payment.ui.web;

import com.openmarket.payment.query.application.PaymentMetricsQueryService;
import com.openmarket.payment.query.dto.PaymentMetricsView;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 결제 지표(ADM-4). ADMIN 역할은 경로 접두어로 검사한다(common-security). */
@RestController
public class PaymentMetricsQueryController {

    private final PaymentMetricsQueryService metrics;

    public PaymentMetricsQueryController(PaymentMetricsQueryService metrics) {
        this.metrics = metrics;
    }

    /**
     * 날짜(KST)별 승인·거절·미정 수와 실패율. 기간은 31일까지이고 넘으면 400 {@code PAYMENT_METRICS_PERIOD_TOO_LONG}.
     *
     * @param from 첫날(포함, {@code yyyy-MM-dd})
     * @param to 마지막 날(포함)
     */
    @GetMapping("/v1/admin/metrics/payments")
    public PaymentMetricsView paymentMetrics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return metrics.metrics(from, to);
    }
}
