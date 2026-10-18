package com.openmarket.member.ui.web;

import com.openmarket.member.query.application.MemberQueryService;
import com.openmarket.member.query.dto.AddressView;
import com.openmarket.member.query.dto.MemberView;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 본인 프로필과 배송지(ACC-3). 주체는 토큰의 {@code sub}(= memberId)다. */
@RestController
@RequestMapping("/v1/members/me")
public class MemberQueryController {

    private final MemberQueryService members;

    public MemberQueryController(MemberQueryService members) {
        this.members = members;
    }

    /** 가입 직후에는 회원이 아직 만들어지지 않아 404 {@code MEMBER_NOT_FOUND}일 수 있다. */
    @GetMapping
    public MemberView me(Principal principal) {
        return members.member(memberId(principal));
    }

    /** 기본 배송지가 먼저, 나머지는 최근에 추가한 순서. */
    @GetMapping("/addresses")
    public List<AddressView> addresses(Principal principal) {
        return members.addresses(memberId(principal));
    }

    static UUID memberId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
