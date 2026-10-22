package com.openmarket.notification.ui.web;

import static com.openmarket.notification.ui.web.NotificationQueryController.accountId;

import com.openmarket.notification.command.application.ReadAllNotificationsCommand;
import com.openmarket.notification.command.application.ReadAllNotificationsUseCase;
import com.openmarket.notification.command.application.ReadNotificationCommand;
import com.openmarket.notification.command.application.ReadNotificationUseCase;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 읽음. 받는 쪽은 경로로 정한다({@link NotificationQueryController}). 다시 해도 204다. 내 알림함에 없는 알림은 404 {@code
 * NOTIFICATION_NOT_FOUND}.
 */
@RestController
public class NotificationCommandController {

    private final ReadNotificationUseCase read;
    private final ReadAllNotificationsUseCase readAll;

    public NotificationCommandController(
            ReadNotificationUseCase read, ReadAllNotificationsUseCase readAll) {
        this.read = read;
        this.readAll = readAll;
    }

    /** 구매자 알림 하나를 읽음으로. */
    @PostMapping("/v1/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void buyerRead(Principal principal, @PathVariable UUID notificationId) {
        read.execute(
                new ReadNotificationCommand(
                        notificationId,
                        ReadNotificationCommand.Audience.BUYER,
                        accountId(principal)));
    }

    /** 구매자 알림함을 모두 읽음으로. */
    @PostMapping("/v1/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void buyerReadAll(Principal principal) {
        readAll.execute(
                new ReadAllNotificationsCommand(
                        ReadNotificationCommand.Audience.BUYER, accountId(principal)));
    }

    /** 판매자 알림 하나를 읽음으로. */
    @PostMapping("/v1/seller/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sellerRead(Principal principal, @PathVariable UUID notificationId) {
        read.execute(
                new ReadNotificationCommand(
                        notificationId,
                        ReadNotificationCommand.Audience.SELLER,
                        accountId(principal)));
    }

    /** 판매자 알림함을 모두 읽음으로. */
    @PostMapping("/v1/seller/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sellerReadAll(Principal principal) {
        readAll.execute(
                new ReadAllNotificationsCommand(
                        ReadNotificationCommand.Audience.SELLER, accountId(principal)));
    }

    /** 관리자 알림 하나를 이 관리자가 읽음으로. 다른 관리자의 읽음은 바뀌지 않는다. */
    @PostMapping("/v1/admin/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminRead(Principal principal, @PathVariable UUID notificationId) {
        read.execute(
                new ReadNotificationCommand(
                        notificationId,
                        ReadNotificationCommand.Audience.ADMIN,
                        accountId(principal)));
    }

    /** 관리자 알림함을 이 관리자가 모두 읽음으로. */
    @PostMapping("/v1/admin/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adminReadAll(Principal principal) {
        readAll.execute(
                new ReadAllNotificationsCommand(
                        ReadNotificationCommand.Audience.ADMIN, accountId(principal)));
    }
}
