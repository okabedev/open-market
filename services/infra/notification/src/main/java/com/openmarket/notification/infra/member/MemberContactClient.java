package com.openmarket.notification.infra.member;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * member 내부 API(docs/architecture/member.md "내부 API"). Eureka로 찾고, 서비스 간 토큰과 서킷 브레이커가
 * 붙는다(config-repo notification.yml). {@code openmarket.notification.member-url}이 있으면 그 주소로 바로
 * 부른다(테스트).
 */
@FeignClient(
        name = "member",
        contextId = "memberContact",
        url = "${openmarket.notification.member-url:}")
interface MemberContactClient {

    @GetMapping("/internal/v1/members/{memberId}/contact")
    ContactResponse contact(@PathVariable("memberId") UUID memberId);

    /** 받는 주소는 로그에 남기지 않는다. */
    record ContactResponse(String email, boolean emailVerified) {

        @Override
        public String toString() {
            return "ContactResponse[emailVerified=" + emailVerified + "]";
        }
    }
}
