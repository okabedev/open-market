package com.openmarket.search.command.application;

import java.util.UUID;

/**
 * 판매 상태 이벤트를 반영한다.
 *
 * @param revision 상품 리비전
 */
public record ChangeProductStatusCommand(UUID productId, long revision, Status status) {

    /**
     * 이벤트가 만드는 상태. {@code ProductStopped} → STOPPED, {@code ProductResumed} → ON_SALE, {@code
     * ProductBanned} → BANNED.
     */
    public enum Status {
        ON_SALE,
        STOPPED,
        BANNED
    }
}
