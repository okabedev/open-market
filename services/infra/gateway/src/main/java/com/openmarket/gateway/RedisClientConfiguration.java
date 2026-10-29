package com.openmarket.gateway;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 클라이언트(Lettuce) 옵션(docs/architecture/data.md "캐시와 기타 저장소"). Boot가 만든 옵션(연결·명령 타임아웃)에 세 가지를
 * 더한다.
 *
 * <ul>
 *   <li>연결이 끊긴 동안 명령을 쌓지 않고 바로 실패시킨다. 기본값은 끊긴 동안 명령을 무제한 버퍼에 쌓았다가 다시 연결되면 보낸다. 레이트 리밋 명령이 쌓여 요청마다
 *       타임아웃을 기다렸고, 요청이 몰리면 힙이 차서 gateway가 멈췄다(docs/architecture/risks.md "장애 주입"). 실패하면 레이트 리미터가
 *       요청을 통과시킨다.
 *   <li>연결은 살아 있는데 응답이 없을 때 쌓이는 명령의 상한. 넘으면 바로 실패한다.
 *   <li>응답 신호를 Lettuce I/O 스레드가 아닌 스케줄러에서 보낸다. 기본값은 I/O 스레드에서 보내서 레이트 리밋 다음의 필터 체인(JWT 검증, 라우팅)까지 그
 *       스레드에서 돌았다. 요청이 몰리면 다른 Redis 응답 해석이 밀려, Redis 서버는 빠른데 명령 타임아웃이 쿠폰 버스트마다 약 1,000건
 *       났다(docs/architecture/risks.md "부하 측정").
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class RedisClientConfiguration {

    /** 연결 하나에 응답을 기다리는 명령 수의 상한. 평소에는 초당 수백 건 × 수 밀리초라 몇 개다. */
    static final int REQUEST_QUEUE_SIZE = 1024;

    @Bean
    LettuceClientOptionsBuilderCustomizer redisClientOptions() {
        return builder ->
                builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .requestQueueSize(REQUEST_QUEUE_SIZE)
                        .publishOnScheduler(true);
    }
}
