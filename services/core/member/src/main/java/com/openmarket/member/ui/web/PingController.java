package com.openmarket.member.ui.web;

import com.openmarket.member.command.application.SendPingUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * gateway 경유 호출과 이벤트 파이프라인을 확인하는 샘플 API(M0 완료 기준). gateway의 공개 경로에 {@code /api/member/v1/ping}으로
 * 등록돼 있다. M1에서 실제 API가 생기면 지운다.
 */
@RestController
class PingController {

    private final SendPingUseCase sendPing;

    PingController(SendPingUseCase sendPing) {
        this.sendPing = sendPing;
    }

    @GetMapping("/v1/ping")
    PingResponse ping() {
        return new PingResponse("member");
    }

    /** 샘플 이벤트 {@code PingSent}를 발행한다. 리스너가 처리하면 member 로그에 남는다. */
    @PostMapping("/v1/ping")
    @ResponseStatus(HttpStatus.ACCEPTED)
    SendPingResponse send() {
        return new SendPingResponse(sendPing.execute());
    }
}
