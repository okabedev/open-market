package com.openmarket.member.infra.order;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.common.core.error.ErrorType;
import com.openmarket.member.command.application.OngoingOrderPort;
import feign.FeignException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link OngoingOrderPort}의 OpenFeign 구현. order를 부를 수 없으면 탈퇴하지 않고 503 {@code
 * MEMBER_ORDER_UNAVAILABLE}이다. 진행 중인 주문을 확인하지 못한 채 탈퇴시키면 되돌릴 수 없기 때문이다.
 */
@Component
class FeignOngoingOrderAdapter implements OngoingOrderPort {

    private static final Logger log = LoggerFactory.getLogger(FeignOngoingOrderAdapter.class);

    private final OngoingOrderClient client;

    FeignOngoingOrderAdapter(OngoingOrderClient client) {
        this.client = client;
    }

    @Override
    public boolean hasOngoingOrders(UUID memberId) {
        try {
            return client.ongoingOrders(memberId).hasOngoingOrders();
        } catch (RuntimeException e) {
            throw unavailable(e);
        }
    }

    /** 서킷 브레이커가 감싼 예외를 503으로 바꾼다. 4xx는 member가 요청을 잘못 만든 것이라 그대로 오류(500)로 둔다. */
    private static RuntimeException unavailable(RuntimeException e) {
        Throwable root = e;
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof FeignException.FeignClientException clientError) {
                return new IllegalStateException("order가 요청을 거절했다: " + clientError.status(), e);
            }
            root = cause;
        }
        log.warn("order를 부를 수 없다: {}", root.toString());
        return new BusinessException(
                ErrorType.UNAVAILABLE,
                "MEMBER_ORDER_UNAVAILABLE",
                "주문 정보를 잠시 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
                e);
    }
}
