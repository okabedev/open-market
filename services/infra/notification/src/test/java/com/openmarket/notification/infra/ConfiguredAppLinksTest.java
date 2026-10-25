package com.openmarket.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.notification.command.domain.NotificationAudience;
import com.openmarket.notification.command.domain.NotificationTarget;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfiguredAppLinksTest {

    final ConfiguredAppLinks links =
            new ConfiguredAppLinks("http://shop", "http://seller", "http://admin");
    final UUID id = UUID.fromString("019a2c3d-4e5f-7a60-8b1c-2d3e4f5a6b70");

    @Test
    @DisplayName("받는 쪽의 앱에서 이동 대상 화면 주소를 만든다")
    void linksToAppScreens() {
        assertThat(links.linkFor(NotificationAudience.BUYER, NotificationTarget.ORDER, id))
                .isEqualTo("http://shop/orders/" + id);
        assertThat(
                        links.linkFor(
                                NotificationAudience.BUYER,
                                NotificationTarget.SELLER_APPLICATION,
                                id))
                .isEqualTo("http://shop/my/seller");
        assertThat(links.linkFor(NotificationAudience.SELLER, NotificationTarget.PRODUCT, id))
                .isEqualTo("http://seller/products/" + id);
        assertThat(links.linkFor(NotificationAudience.SELLER, NotificationTarget.SELLER_ORDER, id))
                .isEqualTo("http://seller/orders/" + id);
    }

    @Test
    @DisplayName("대상이 없거나 화면이 아직 없으면 앱의 첫 화면이다")
    void fallsBackToAppHome() {
        assertThat(links.linkFor(NotificationAudience.SELLER, null, null))
                .isEqualTo("http://seller/");
        assertThat(links.linkFor(NotificationAudience.ADMIN, NotificationTarget.REFUND, id))
                .isEqualTo("http://admin/");
        assertThat(links.linkFor(NotificationAudience.SELLER, NotificationTarget.REVIEW, id))
                .isEqualTo("http://seller/");
        assertThat(links.linkFor(NotificationAudience.SELLER, NotificationTarget.SETTLEMENT, id))
                .isEqualTo("http://seller/");
    }
}
