package com.openmarket.notification.ui.web;

import com.openmarket.notification.query.application.NotificationQueryService;
import com.openmarket.notification.query.dto.CursorPageView;
import com.openmarket.notification.query.dto.NotificationAudience;
import com.openmarket.notification.query.dto.NotificationView;
import com.openmarket.notification.query.dto.UnreadCountView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 조회(docs/architecture/notification.md "인앱 알림"). 받는 쪽은 경로로 정한다: 구매자 {@code /v1/notifications},
 * 판매자 {@code /v1/seller/notifications}, 관리자 {@code /v1/admin/notifications}. 보는 회원은 토큰의 {@code
 * sub}다. 목록은 최근 순 커서 목록이고, 커서가 틀리면 400 {@code NOTIFICATION_INVALID_CURSOR}.
 */
@RestController
public class NotificationQueryController {

    private final NotificationQueryService notifications;

    public NotificationQueryController(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    /** 구매자 알림함(shop). */
    @GetMapping("/v1/notifications")
    public CursorPageView<NotificationView> buyerInbox(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return notifications.inbox(NotificationAudience.BUYER, accountId(principal), cursor, size);
    }

    /** 구매자 알림함의 안 읽은 수. */
    @GetMapping("/v1/notifications/unread-count")
    public UnreadCountView buyerUnreadCount(Principal principal) {
        return notifications.unreadCount(NotificationAudience.BUYER, accountId(principal));
    }

    /** 판매자 알림함(seller). */
    @GetMapping("/v1/seller/notifications")
    public CursorPageView<NotificationView> sellerInbox(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return notifications.inbox(NotificationAudience.SELLER, accountId(principal), cursor, size);
    }

    /** 판매자 알림함의 안 읽은 수. */
    @GetMapping("/v1/seller/notifications/unread-count")
    public UnreadCountView sellerUnreadCount(Principal principal) {
        return notifications.unreadCount(NotificationAudience.SELLER, accountId(principal));
    }

    /** 관리자 알림함(admin). 관리자 모두에게 같은 알림이고, 읽음만 관리자마다 다르다. */
    @GetMapping("/v1/admin/notifications")
    public CursorPageView<NotificationView> adminInbox(
            Principal principal,
            @RequestParam(required = false) @Nullable String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return notifications.inbox(NotificationAudience.ADMIN, accountId(principal), cursor, size);
    }

    /** 관리자 알림함의 안 읽은 수(이 관리자가 읽지 않은 수). */
    @GetMapping("/v1/admin/notifications/unread-count")
    public UnreadCountView adminUnreadCount(Principal principal) {
        return notifications.unreadCount(NotificationAudience.ADMIN, accountId(principal));
    }

    static UUID accountId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
