package com.openmarket.fulfillment.ui.web;

import com.openmarket.fulfillment.query.application.FulfillmentQueryService;
import com.openmarket.fulfillment.query.dto.MediationClaimView;
import com.openmarket.fulfillment.query.dto.PageView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자의 중재 목록(ADM-2, docs/architecture/fulfillment.md "중재"). */
@RestController
@RequestMapping("/v1/admin/claims")
public class AdminClaimQueryController {

    private final FulfillmentQueryService fulfillments;

    public AdminClaimQueryController(FulfillmentQueryService fulfillments) {
        this.fulfillments = fulfillments;
    }

    /** 중재 중인 클레임. 중재 요청이 오래된 순, 쪽 번호. */
    @GetMapping
    public PageView<MediationClaimView> mediating(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return fulfillments.mediatingClaims(page, size);
    }
}
