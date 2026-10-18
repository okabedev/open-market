package com.openmarket.member.ui.web;

import static com.openmarket.member.ui.web.MemberQueryController.memberId;

import com.openmarket.member.query.application.SellerQueryService;
import com.openmarket.member.query.dto.MySellerApplicationView;
import com.openmarket.member.query.dto.SellerView;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 입점 신청 이력과 판매자 본인 정보. */
@RestController
public class SellerApplicationQueryController {

    private final SellerQueryService sellers;

    public SellerApplicationQueryController(SellerQueryService sellers) {
        this.sellers = sellers;
    }

    /** 본인 입점 신청 이력. 최근 신청이 먼저다. */
    @GetMapping("/v1/members/me/seller-applications")
    public List<MySellerApplicationView> myApplications(Principal principal) {
        return sellers.myApplications(memberId(principal));
    }

    /** 판매자 본인 정보(판매자 앱). {@code SELLER} 역할이 있어야 한다. */
    @GetMapping("/v1/seller/me")
    public SellerView me(Principal principal) {
        return sellers.seller(memberId(principal));
    }
}
