package com.openmarket.notification.infra;

import com.openmarket.notification.command.application.EmailRendererPort;
import com.openmarket.notification.command.application.MailSenderPort;
import com.openmarket.notification.infra.mail.SpringMailSender;
import com.openmarket.notification.infra.mail.ThymeleafEmailRenderer;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** notification의 어댑터: SMTP 발송, 메일 템플릿, 조회(QueryDSL). */
@Configuration(proxyBeanMethods = false)
public class NotificationInfraConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }

    @Bean
    MailSenderPort smtpMailSender(
            JavaMailSender javaMailSender,
            @Value("${openmarket.notification.mail-from}") String from) {
        return new SpringMailSender(javaMailSender, from);
    }

    @Bean
    EmailRendererPort emailRenderer() {
        return new ThymeleafEmailRenderer(
                engine(TemplateMode.HTML, ".html"), engine(TemplateMode.TEXT, ".txt"));
    }

    private static TemplateEngine engine(TemplateMode mode, String suffix) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
