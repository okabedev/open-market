package com.openmarket.member.ui.web;

import com.openmarket.member.query.application.MemberQueryService;
import com.openmarket.member.query.dto.MemberContactView;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 조회. {@code internal} scope 토큰만 부른다(common-security). gateway는 {@code /internal/**}을 넘기지
 * 않는다.
 */
@RestController
public class InternalMemberQueryController {

    private final MemberQueryService members;

    public InternalMemberQueryController(MemberQueryService members) {
        this.members = members;
    }

    /** 알림 이메일의 받는 주소. notification이 보낼 때 부른다. 없거나 탈퇴한 회원은 404 {@code MEMBER_NOT_FOUND}. */
    @GetMapping("/internal/v1/members/{memberId}/contact")
    public MemberContactView contact(@PathVariable UUID memberId) {
        return members.contact(memberId);
    }
}
