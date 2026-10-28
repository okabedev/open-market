package com.openmarket.fulfillment.command.application;

import com.openmarket.common.messaging.consumer.IgnoredEvents;
import com.openmarket.fulfillment.command.domain.Claim;
import com.openmarket.fulfillment.command.domain.ClaimRepository;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 실패 코드를 클레임에 남긴다(docs/architecture/fulfillment.md "환불 결과 반영"). 클레임은 {@code APPROVED}로 남고 이벤트는
 * 내지 않는다. 관리자가 payment에서 다시 시도한다.
 */
@Service
public class RecordRefundFailureUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecordRefundFailureUseCase.class);

    private final ClaimRepository claims;
    private final Clock clock;

    public RecordRefundFailureUseCase(ClaimRepository claims, Clock clock) {
        this.claims = claims;
        this.clock = clock;
    }

    @Transactional
    public void execute(RecordRefundFailureCommand command) {
        Optional<Claim> found = claims.findForUpdateById(command.claimId());
        if (found.isEmpty()) {
            IgnoredEvents.warn(log, "모르는 클레임의 환불 실패라 넘긴다: claimId={}", command.claimId());
            return;
        }
        Claim claim = found.get();
        if (claim.recordRefundFailure(command.code(), clock.instant())) {
            claims.save(claim);
        }
    }
}
