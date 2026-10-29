package com.openmarket.common.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    @Test
    @DisplayName("toString은 페이로드를 남기지 않는다. 페이로드에 개인정보가 들어갈 수 있다(NFR-7)")
    void toStringHidesPayload() {
        UUID id = UUID.randomUUID();
        EventEnvelope<Payload> envelope =
                new EventEnvelope<>(
                        id,
                        "AccountRegistered",
                        "account-1",
                        Instant.parse("2026-10-29T00:00:00Z"),
                        1,
                        null,
                        new Payload("buyer@example.com"));

        assertThat(envelope.toString())
                .contains(id.toString())
                .contains("AccountRegistered")
                .doesNotContain("buyer@example.com");
    }

    private record Payload(String email) {}
}
