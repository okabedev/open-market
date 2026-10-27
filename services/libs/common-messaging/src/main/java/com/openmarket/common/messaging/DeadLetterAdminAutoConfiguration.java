package com.openmarket.common.messaging;

import com.openmarket.common.messaging.deadletter.DeadLetterAdminController;
import com.openmarket.common.messaging.deadletter.DeadLetterService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * DLT 메시지 관리자 API(ADR 0022). 웹(MVC) 서비스에만 둔다. 라이브러리는 spring-web을 강제하지 않는다. {@link
 * DeadLetterAutoConfiguration}이 만든 서비스 빈이 있어야 하므로 그 뒤에 처리한다.
 */
@AutoConfiguration(after = DeadLetterAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnBean(DeadLetterService.class)
public class DeadLetterAdminAutoConfiguration {

    @Bean
    DeadLetterAdminController deadLetterAdminController(DeadLetterService deadLetterService) {
        return new DeadLetterAdminController(deadLetterService);
    }
}
