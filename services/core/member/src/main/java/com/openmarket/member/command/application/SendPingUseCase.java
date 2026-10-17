package com.openmarket.member.command.application;

import com.openmarket.common.core.id.Ids;
import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.PingSent;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 샘플 이벤트 {@link PingSent}를 outbox에 쌓는다(M0). M1에서 지운다. */
@Service
public class SendPingUseCase {

    static final String TOPIC = "member.ping";
    static final String EVENT_TYPE = "PingSent";

    private final Outbox outbox;

    public SendPingUseCase(Outbox outbox) {
        this.outbox = outbox;
    }

    /** 새 샘플 ID로 이벤트를 쌓고 그 ID를 돌려준다. */
    @Transactional
    public UUID execute() {
        UUID pingId = Ids.newId();
        outbox.append(TOPIC, pingId.toString(), EVENT_TYPE, new PingSent(pingId));
        return pingId;
    }
}
