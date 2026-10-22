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
 * Thymeleaf 템플릿 두 개(HTML, 텍스트)로 본문을 만든다. 가입 인증은 {@code templates/mail/email-verification}, 나머지 종류는
 * 인앱 문구와 앱 링크를 넣는 공통 템플릿 {@code templates/mail/notice}다. 제목은 종류마다 다르다. 시각 값({@code ...At})은 한국 시간으로
 * 바꿔 넣는다.
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
        String template =
                kind == NotificationKind.EMAIL_VERIFICATION
                        ? "mail/email-verification"
                        : "mail/notice";
        return new RenderedEmail(
                subject(kind),
                htmlEngine.process(template, context),
                textEngine.process(template, context));
    }

    private static String subject(NotificationKind kind) {
        return switch (kind) {
            case EMAIL_VERIFICATION -> "[오픈 마켓] 이메일 인증을 완료해 주세요";
            case ORDER_PAID -> "[오픈 마켓] 주문이 완료됐습니다";
            case SHIPMENT_SHIPPED -> "[오픈 마켓] 주문하신 상품이 발송됐습니다";
            case CLAIM_APPROVED -> "[오픈 마켓] 반품·교환 신청이 승인됐습니다";
            case CLAIM_REJECTED -> "[오픈 마켓] 반품·교환 신청이 거부됐습니다";
            case CLAIM_COMPLETED -> "[오픈 마켓] 취소·반품·교환이 완료됐습니다";
            case SELLER_APPROVED -> "[오픈 마켓] 입점 신청이 승인됐습니다";
            case SELLER_REJECTED -> "[오픈 마켓] 입점 신청이 반려됐습니다";
            case PRODUCT_BANNED -> "[오픈 마켓] 상품이 판매 중지됐습니다";
            default -> throw new IllegalArgumentException("이메일을 보내지 않는 알림 종류: " + kind);
        };
    }
}
