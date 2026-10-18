package com.openmarket.member.infra;

import com.openmarket.member.command.application.OngoingOrderPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * order 서비스가 생기기 전(M1·M2)의 구현. 항상 진행 중인 주문이 없다고 답한다. M3에서 order 내부 API 호출로 바꾼다(docs/prd.md ACC-4).
 */
@Component
class StubOngoingOrderAdapter implements OngoingOrderPort {

    @Override
    public boolean hasOngoingOrders(UUID memberId) {
        return false;
    }
}
