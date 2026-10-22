package com.openmarket.notification.infra.mail;

import com.openmarket.notification.command.application.EmailRendererPort;
import com.openmarket.notification.command.domain.NotificationKind;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * 알림 종류마다 Thymeleaf 템플릿 두 개로 본문을 만든다: {@code templates/mail/<종류>.html}, {@code
 * templates/mail/<종류>.txt}. 시각 값({@code ...At})은 한국 시간으로 바꿔 넣는다.
 */
public class ThymeleafEmailRenderer implements EmailRendererPort {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREAN);

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public ThymeleafEmailRenderer(TemplateEngine htmlEngine, TemplateEngine textEngine) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
    }

    @Override
    public RenderedEmail render(NotificationKind kind, Map<String, String> params) {
        Context context = new Context(Locale.KOREAN);
        Map<String, Object> values = new HashMap<>(params);
        params.forEach(
                (name, value) -> {
                    if (name.endsWith("At")) {
                        values.put(name, DISPLAY.format(Instant.parse(value).atZone(KST)));
                    }
                });
        context.setVariables(values);
        String template = "mail/" + kind.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return new RenderedEmail(
                subject(kind),
                htmlEngine.process(template, context),
                textEngine.process(template, context));
    }

    private static String subject(NotificationKind kind) {
        return switch (kind) {
            case EMAIL_VERIFICATION -> "[오픈 마켓] 이메일 인증을 완료해 주세요";
            default -> throw new IllegalArgumentException("메일 템플릿이 없는 알림 종류: " + kind);
        };
    }
}
