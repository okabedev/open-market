package com.openmarket.notification.query.application;

import com.openmarket.notification.query.dao.NotificationQueryDao;
import com.openmarket.notification.query.dto.CursorPageView;
import com.openmarket.notification.query.dto.NotificationAudience;
import com.openmarket.notification.query.dto.NotificationView;
import com.openmarket.notification.query.dto.UnreadCountView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림함 조회. 받는 쪽은 API 경로로, 보는 회원은 토큰의 {@code sub}로 정한다. */
@Service
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationQueryDao dao;

    public NotificationQueryService(NotificationQueryDao dao) {
        this.dao = dao;
    }

    public CursorPageView<NotificationView> inbox(
            NotificationAudience audience, UUID accountId, @Nullable String cursor, int size) {
        return dao.findInbox(audience, accountId, cursor, size);
    }

    public UnreadCountView unreadCount(NotificationAudience audience, UUID accountId) {
        return new UnreadCountView(dao.countUnread(audience, accountId));
    }
}
