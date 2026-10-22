package com.openmarket.notification.infra;

import com.openmarket.notification.command.application.AppLinkPort;
import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationTarget;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 앱 주소({@code openmarket.notification.app-urls})로 화면 주소를 만든다. 화면 경로는 앱(clients/apps)의 라우트와 같다. 화면이
 * 아직 없는 대상(관리자 클레임·환불, 판매자 클레임)은 앱의 첫 화면이나 목록이다.
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
                    case CLAIM -> audience == NotificationAudience.SELLER ? "/orders" : "/";
                    case REFUND -> "/";
                };
    }
}
