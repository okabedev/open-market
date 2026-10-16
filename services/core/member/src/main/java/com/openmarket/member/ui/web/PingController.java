package com.openmarket.member.ui.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * gateway 경유 호출을 확인하는 샘플 API(M0 완료 기준). gateway의 공개 경로에 {@code GET /api/member/v1/ping}으로 등록돼 있다.
 * M1에서 실제 API가 생기면 지운다.
 */
@RestController
class PingController {

    @GetMapping("/v1/ping")
    PingResponse ping() {
        return new PingResponse("member");
    }
}
