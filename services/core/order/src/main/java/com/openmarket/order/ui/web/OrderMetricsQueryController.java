package com.openmarket.order.ui.web;

import com.openmarket.order.query.application.OrderMetricsQueryService;
import com.openmarket.order.query.dto.OrderMetricsView;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 주문 지표(ADM-4). ADMIN 역할은 경로 접두어로 검사한다(common-security). */
@RestController
public class OrderMetricsQueryController {

    private final OrderMetricsQueryService metrics;

    public OrderMetricsQueryController(OrderMetricsQueryService metrics) {
        this.metrics = metrics;
    }

    /**
     * 날짜(KST)별 주문 수, 결제된 주문 수, GMV. 기간은 31일까지이고 넘으면 400 {@code ORDER_METRICS_PERIOD_TOO_LONG}.
     *
     * @param from 첫날(포함, {@code yyyy-MM-dd})
     * @param to 마지막 날(포함)
     */
    @GetMapping("/v1/admin/metrics/orders")
    public OrderMetricsView orderMetrics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return metrics.metrics(from, to);
    }
}
