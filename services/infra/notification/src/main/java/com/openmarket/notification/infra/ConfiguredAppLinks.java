package com.openmarket.notification.infra;

import com.openmarket.notification.command.application.AppLinkPort;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationTarget;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 앱 주소({@code openmarket.notification.app-urls})로 화면 주소를 만든다. 화면 경로는 앱(clients/apps)의 라우트와 같다. 판매자
 * 리뷰는 한 건 화면이 없어 리뷰 목록의 그 리뷰다. 관리자 클레임·환불과 판매자 클레임(M5 전에 만든 알림)은 목록 화면이다. 앱의 알림 이동({@code
 * clients/apps/<앱>/lib/notifications.ts})과 같다.
 */
@Component
class ConfiguredAppLinks implements AppLinkPort {

    private final String shopUrl;
    private final String sellerUrl;
    private final String adminUrl;

    ConfiguredAppLinks(
            @Value("${openmarket.notification.app-urls.shop}") String shopUrl,
            @Value("${openmarket.notification.app-urls.seller}") String sellerUrl,
            @Value("${openmarket.notification.app-urls.admin}") String adminUrl) {
        this.shopUrl = shopUrl;
        this.sellerUrl = sellerUrl;
        this.adminUrl = adminUrl;
    }

    @Override
    public String linkFor(
            NotificationAudience audience,
            @Nullable NotificationTarget target,
            @Nullable UUID targetId) {
        String app =
                switch (audience) {
                    case BUYER -> shopUrl;
                    case SELLER -> sellerUrl;
                    case ADMIN -> adminUrl;
                };
        if (target == null || targetId == null) {
            return app + "/";
        }
        return app
                + switch (target) {
                    case ORDER -> "/orders/" + targetId;
                    case SELLER_ORDER -> "/orders/" + targetId;
                    case PRODUCT -> "/products/" + targetId;
                    case SELLER_APPLICATION -> "/my/seller";
                    case CLAIM ->
                            audience == NotificationAudience.SELLER
                                    ? "/orders?status=claims"
                                    : "/claims";
                    case REVIEW -> "/reviews#review-" + targetId;
                    case SETTLEMENT -> "/settlements/" + targetId;
                    case REFUND -> "/payment-failures";
                };
    }
}
