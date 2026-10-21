package com.openmarket.payment.infra;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** payment의 빈: 시계. */
@Configuration(proxyBeanMethods = false)
public class PaymentInfraConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
