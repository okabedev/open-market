package com.openmarket.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.openapi.OpenApiSnapshot;
import com.openmarket.notification.command.application.NotifyCommand;
import com.openmarket.notification.command.application.NotifyUseCase;
import com.openmarket.notification.infra.NotificationPurgeJob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 알림함 API(docs/architecture/notification.md "인앱 알림")와 보관 기간 정리. */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "openmarket.notification.purge-interval=1h"
        })
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class NotificationApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired NotifyUseCase notify;

    @Autowired NotificationPurgeJob purgeJob;

    @Autowired JdbcClient jdbc;

    @Autowired JsonMapper json;

    final UUID memberId = UUID.randomUUID();

    @Test
    @DisplayName("구매자 알림함은 내 구매자 알림만 최근 순 커서 목록으로 준다. 판매자 알림과 남의 알림은 없다")
    void buyerInbox() throws Exception {
        UUID first =
                notice(
                        NotifyCommand.Audience.BUYER,
                        memberId,
                        NotifyCommand.Kind.SHIPMENT_DELIVERED);
        UUID second =
                notice(
                        NotifyCommand.Audience.BUYER,
                        memberId,
                        NotifyCommand.Kind.SHIPMENT_DELIVERED);
        UUID third =
                notice(
                        NotifyCommand.Audience.BUYER,
                        memberId,
                        NotifyCommand.Kind.SHIPMENT_DELIVERED);
        notice(NotifyCommand.Audience.SELLER, memberId, NotifyCommand.Kind.SELLER_APPROVED);
        notice(
                NotifyCommand.Audience.BUYER,
                UUID.randomUUID(),
                NotifyCommand.Kind.SHIPMENT_DELIVERED);

        JsonNode page = read("/v1/notifications?size=2", buyer(memberId));
        assertThat(ids(page)).containsExactly(third, second);
        assertThat(page.get("totalCount").asLong()).isEqualTo(3);
        JsonNode item = page.get("items").get(0);
        assertThat(item.get("kind").asString()).isEqualTo("SHIPMENT_DELIVERED");
        assertThat(item.get("message").asString()).isEqualTo("주문하신 상품의 배송이 완료됐습니다.");
        assertThat(item.get("targetType").asString()).isEqualTo("ORDER");
        assertThat(item.get("read").asBoolean()).isFalse();

        JsonNode next =
                read(
                        "/v1/notifications?size=2&cursor=" + page.get("nextCursor").asString(),
                        buyer(memberId));
        assertThat(ids(next)).containsExactly(first);
        assertThat(next.get("nextCursor").isNull()).isTrue();

        mockMvc.perform(get("/v1/notifications?cursor=broken").with(buyer(memberId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_INVALID_CURSOR"));
        assertThat(read("/v1/notifications/unread-count", buyer(memberId)).get("count").asLong())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("읽음은 다시 해도 204이고 안 읽은 수가 줄어든다. 남의 알림이나 다른 받는 쪽의 알림은 404다. 모두 읽음은 지금까지 온 알림을 읽는다")
    void readsNotifications() throws Exception {
        UUID mine =
                notice(NotifyCommand.Audience.SELLER, memberId, NotifyCommand.Kind.SELLER_APPROVED);
        notice(NotifyCommand.Audience.SELLER, memberId, NotifyCommand.Kind.SELLER_APPROVED);
        notice(NotifyCommand.Audience.SELLER, memberId, NotifyCommand.Kind.SELLER_APPROVED);
        UUID others =
                notice(
                        NotifyCommand.Audience.SELLER,
                        UUID.randomUUID(),
                        NotifyCommand.Kind.SELLER_APPROVED);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(
                            post("/v1/seller/notifications/" + mine + "/read")
                                    .with(seller(memberId)))
                    .andExpect(status().isNoContent());
        }
        assertThat(unread("/v1/seller", seller(memberId))).isEqualTo(2);
        JsonNode page = read("/v1/seller/notifications", seller(memberId));
        assertThat(page.get("items").get(2).get("read").asBoolean()).isTrue();

        mockMvc.perform(post("/v1/seller/notifications/" + others + "/read").with(seller(memberId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(post("/v1/notifications/" + mine + "/read").with(buyer(memberId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/seller/notifications").with(buyer(memberId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/seller/notifications/read-all").with(seller(memberId)))
                .andExpect(status().isNoContent());
        assertThat(unread("/v1/seller", seller(memberId))).isZero();
        notice(NotifyCommand.Audience.SELLER, memberId, NotifyCommand.Kind.SELLER_APPROVED);
        assertThat(unread("/v1/seller", seller(memberId))).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 알림은 관리자 모두의 알림함에 하나씩 있고, 읽음은 관리자마다 따로다. 관리자가 아니면 403이다")
    void adminInbox() throws Exception {
        UUID firstAdmin = UUID.randomUUID();
        UUID secondAdmin = UUID.randomUUID();
        long before = unread("/v1/admin", admin(firstAdmin));
        UUID mediation =
                notice(
                        NotifyCommand.Audience.ADMIN,
                        null,
                        NotifyCommand.Kind.CLAIM_MEDIATION_REQUESTED);

        assertThat(unread("/v1/admin", admin(firstAdmin))).isEqualTo(before + 1);
        assertThat(ids(read("/v1/admin/notifications", admin(secondAdmin)))).contains(mediation);

        mockMvc.perform(
                        post("/v1/admin/notifications/" + mediation + "/read")
                                .with(admin(firstAdmin)))
                .andExpect(status().isNoContent());
        assertThat(unread("/v1/admin", admin(firstAdmin))).isEqualTo(before);
        assertThat(unread("/v1/admin", admin(secondAdmin))).isEqualTo(before + 1);
        mockMvc.perform(post("/v1/admin/notifications/read-all").with(admin(secondAdmin)))
                .andExpect(status().isNoContent());
        assertThat(unread("/v1/admin", admin(secondAdmin))).isZero();

        mockMvc.perform(get("/v1/admin/notifications").with(seller(memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/notifications/" + mediation + "/read").with(buyer(firstAdmin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("보관 기간이 지난 알림은 읽음 기록과 함께 지운다. 보낼 차례를 기다리는 이메일은 남긴다")
    void purgesOldNotifications() throws Exception {
        UUID old =
                notice(
                        NotifyCommand.Audience.BUYER,
                        memberId,
                        NotifyCommand.Kind.SHIPMENT_DELIVERED);
        UUID recent =
                notice(
                        NotifyCommand.Audience.BUYER,
                        memberId,
                        NotifyCommand.Kind.SHIPMENT_DELIVERED);
        mockMvc.perform(post("/v1/notifications/read-all").with(buyer(memberId)))
                .andExpect(status().isNoContent());
        UUID pendingEmail = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO notification (id, source_event_id, channel, kind, recipient_account_id,
                            recipient_email, status, attempts, next_attempt_at, created_at, version)
                        VALUES (:id, :id, 'EMAIL', 'EMAIL_VERIFICATION', :member, 'a@example.com',
                            'PENDING', 4, now(), now() - interval '100 days', 0)
                        """)
                .param("id", pendingEmail)
                .param("member", memberId)
                .update();
        jdbc.sql(
                        "UPDATE notification SET created_at = now() - interval '91 days' WHERE id ="
                                + " :id")
                .param("id", old)
                .update();

        assertThat(purgeJob.purge()).isGreaterThanOrEqualTo(1);

        assertThat(exists(old)).isFalse();
        assertThat(exists(recent)).isTrue();
        assertThat(exists(pendingEmail)).isTrue();
        assertThat(
                        jdbc.sql(
                                        "SELECT count(*) FROM notification_read WHERE notification_id = :id")
                                .param("id", old)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    @DisplayName("OpenAPI 문서가 프론트 api-client에 저장된 문서와 같다")
    void openApiMatchesClientSnapshot() throws Exception {
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        OpenApiSnapshot.assertMatches("notification", apiDocs);
    }

    /** 인앱 알림 하나를 만들고 ID를 돌려준다. */
    private UUID notice(
            NotifyCommand.Audience audience, @Nullable UUID recipient, NotifyCommand.Kind kind) {
        UUID eventId = UUID.randomUUID();
        notify.execute(
                new NotifyCommand(
                        eventId,
                        List.of(
                                new NotifyCommand.Notice(
                                        kind,
                                        audience,
                                        recipient,
                                        Map.of(
                                                "type", "RETURN",
                                                "storeName", "길동상회"),
                                        NotifyCommand.Target.ORDER,
                                        UUID.randomUUID()))));
        return jdbc.sql(
                        "SELECT id FROM notification WHERE source_event_id = :id AND channel = 'IN_APP'")
                .param("id", eventId)
                .query(UUID.class)
                .single();
    }

    private JsonNode read(String path, RequestPostProcessor token) throws Exception {
        return json.readTree(
                mockMvc.perform(get(path).with(token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private long unread(String prefix, RequestPostProcessor token) throws Exception {
        return read(prefix + "/notifications/unread-count", token).get("count").asLong();
    }

    private static List<UUID> ids(JsonNode page) {
        List<UUID> ids = new ArrayList<>();
        page.get("items")
                .forEach(item -> ids.add(UUID.fromString(item.get("notificationId").asString())));
        return ids;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("SELECT count(*) FROM notification WHERE id = :id")
                        .param("id", id)
                        .query(Integer.class)
                        .single()
                > 0;
    }

    private static RequestPostProcessor buyer(UUID id) {
        return token(id, "BUYER");
    }

    private static RequestPostProcessor seller(UUID id) {
        return token(id, "BUYER", "SELLER");
    }

    private static RequestPostProcessor admin(UUID id) {
        return token(id, "ADMIN");
    }

    private static RequestPostProcessor token(UUID id, String... roles) {
        return jwt().jwt(token -> token.subject(id.toString()).claim("roles", List.of(roles)))
                .authorities(
                        Arrays.stream(roles)
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .toArray(SimpleGrantedAuthority[]::new));
    }
}
