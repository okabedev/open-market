package com.openmarket.member.ui.web;

import com.openmarket.member.query.application.SellerQueryService;
import com.openmarket.member.query.dto.PageView;
import com.openmarket.member.query.dto.SellerApplicationReviewView;
import com.openmarket.member.query.dto.SellerApplicationStatus;
import com.openmarket.member.query.dto.SellerReviewView;
import com.openmarket.member.query.dto.SellerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 입점 심사·판매자 목록(ADM-1). 쪽 번호 목록이다. 개인정보는 마스킹해서 준다(NFR-7). */
@RestController
@RequestMapping("/v1/admin")
public class SellerReviewQueryController {

    private final SellerQueryService sellers;

    public SellerReviewQueryController(SellerQueryService sellers) {
        this.sellers = sellers;
    }

    /** 상태를 주지 않으면 심사 중({@code SUBMITTED})만 준다. 오래된 신청부터. */
    @GetMapping("/seller-applications")
    public PageView<SellerApplicationReviewView> applications(
            @RequestParam(defaultValue = "SUBMITTED") SellerApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sellers.applicationsForReview(status, page, size);
    }

    /** 상태를 주지 않으면 전체. 최근 승인 순. */
    @GetMapping("/sellers")
    public PageView<SellerReviewView> sellers(
            @RequestParam(required = false) @Nullable SellerStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sellers.sellers(status, page, size);
    }
}
