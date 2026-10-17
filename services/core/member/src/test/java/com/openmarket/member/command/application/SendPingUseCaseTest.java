package com.openmarket.member.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.member.command.domain.PingSent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendPingUseCaseTest {

    final RecordingOutbox outbox = new RecordingOutbox();

    final SendPingUseCase useCase = new SendPingUseCase(outbox);

    @Test
    @DisplayName("샘플 ID를 키로 PingSent 이벤트를 member.ping 토픽에 쌓는다")
    void appendsPingSent() {
        UUID pingId = useCase.execute();

        assertThat(outbox.appended)
                .containsExactly(
                        new Appended(
                                "member.ping",
                                pingId.toString(),
                                "PingSent",
                                1,
                                new PingSent(pingId)));
    }

    @Test
    @DisplayName("호출마다 새 샘플 ID를 만든다")
    void createsNewIdEachTime() {
        assertThat(useCase.execute()).isNotEqualTo(useCase.execute());
    }

    record Appended(
            String aggregateType,
            String aggregateId,
            String type,
            int schemaVersion,
            Object payload) {}

    static class RecordingOutbox implements Outbox {

        final List<Appended> appended = new ArrayList<>();

        @Override
        public UUID append(
                String aggregateType,
                String aggregateId,
                String type,
                int schemaVersion,
                Object payload) {
            appended.add(new Appended(aggregateType, aggregateId, type, schemaVersion, payload));
            return UUID.randomUUID();
        }
    }
}
