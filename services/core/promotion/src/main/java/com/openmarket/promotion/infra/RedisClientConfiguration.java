package com.openmarket.promotion.infra;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 클라이언트(Lettuce) 옵션(docs/architecture/data.md "캐시와 기타 저장소"). Boot가 만든 옵션(연결·명령 타임아웃)에 두 가지를
 * 더한다.
 *
 * <ul>
 *   <li>연결이 끊긴 동안 명령을 쌓지 않고 바로 실패시킨다(발급은 503). 기본값은 끊긴 동안 명령을 버퍼에 쌓았다가 다시 연결되면 보낸다. 발급마다 타임아웃을
 *       기다렸고, 다시 연결되는 순간 쌓인 차지 스크립트를 한꺼번에 보내느라 10초 넘게 멈췄다. 호출자에게 이미 503을 준 차지가 나중에 실행되기도
 *       했다(docs/architecture/risks.md "장애 주입").
 *   <li>연결은 살아 있는데 응답이 없을 때 쌓이는 명령의 상한. 넘으면 바로 실패한다.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class RedisClientConfiguration {

    /** 연결 하나에 응답을 기다리는 명령 수의 상한. */
    static final int REQUEST_QUEUE_SIZE = 1024;

    @Bean
    LettuceClientOptionsBuilderCustomizer redisClientOptions() {
        return builder ->
                builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .requestQueueSize(REQUEST_QUEUE_SIZE);
    }
}
