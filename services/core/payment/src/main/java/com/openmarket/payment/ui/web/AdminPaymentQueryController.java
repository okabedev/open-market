package com.openmarket.payment.ui.web;

import com.openmarket.payment.query.application.PaymentQueryService;
import com.openmarket.payment.query.dto.CancelFailureView;
import com.openmarket.payment.query.dto.FailedRefundView;
import com.openmarket.payment.query.dto.PageView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 재시도 목록(docs/architecture/payment.md "관리자 재시도"). 경로 접두어로 {@code ADMIN} 역할을 검사한다. */
@RestController
public class AdminPaymentQueryController {

    private final PaymentQueryService payments;

    public AdminPaymentQueryController(PaymentQueryService payments) {
        this.payments = payments;
    }

    /** 실패한 환불. 오래된 순, 쪽 번호. */
    @GetMapping("/v1/admin/refunds/failed")
    public PageView<FailedRefundView> failedRefunds(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return payments.failedRefunds(page, size);
    }

    /** 토스가 보상 취소를 거절한 결제. 오래된 취소 요청 순, 쪽 번호. */
    @GetMapping("/v1/admin/payments/cancel-failures")
    public PageView<CancelFailureView> cancelFailures(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return payments.cancelFailures(page, size);
    }
}
