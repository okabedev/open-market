package com.openmarket.common.messaging.consumer;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리스너가 이벤트를 한 번만 처리하게 한다. 리스너({@code ui.event})는 받은 메시지를 이 클래스에 넘긴다.
 *
 * <pre>{@code
 * @KafkaListener(topics = "order.order", groupId = "catalog")
 * void on(ConsumerRecord<String, String> record) {
 *     processor.process("catalog.order-placed", record, OrderPlacedMessage.class,
 *             event -> reserveStock.execute(...));
 * }
 * }</pre>
 *
 * <p>트랜잭션 하나에서 {@code processed_event}에 {@code (이벤트 ID, 리스너 이름)}을 넣고 핸들러를 실행한다. 이미 있는 키면 핸들러를 건너뛴다.
 * 핸들러가 예외를 던지면 기록도 롤백되어 재시도할 때 다시 처리한다. 핸들러가 쌓은 outbox 이벤트도 같은 트랜잭션에 묶인다.
 */
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final EventEnvelopeReader reader;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public IdempotentEventProcessor(
            EventEnvelopeReader reader,
            JdbcClient jdbc,
            TransactionTemplate transaction,
            Clock clock) {
        this.reader = reader;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * 처음 보는 이벤트면 핸들러를 실행한다.
     *
     * @param listener 리스너 이름. 서비스 안에서 겹치지 않게 {@code <서비스>.<이벤트>} 형식으로 짓는다. 예: {@code
     *     catalog.order-placed}
     * @return 핸들러를 실행했으면 {@code true}, 이미 처리한 이벤트라 건너뛰었으면 {@code false}
     * @throws InvalidEventException 헤더나 페이로드를 읽을 수 없을 때. 영구 오류다
     */
    public <T> boolean process(
            String listener,
            ConsumerRecord<String, String> record,
            Class<T> payloadType,
            Consumer<EventEnvelope<T>> handler) {
        EventEnvelope<T> event = reader.read(record, payloadType);
        Boolean processed =
                transaction.execute(
                        status -> {
                            if (!markProcessed(event, listener)) {
                                return false;
                            }
                            handler.accept(event);
                            return true;
                        });
        if (!Boolean.TRUE.equals(processed)) {
            log.debug("이미 처리한 이벤트를 건너뛴다: {} {} ({})", event.type(), event.id(), listener);
            return false;
        }
        return true;
    }

    private boolean markProcessed(EventEnvelope<?> event, String listener) {
        return jdbc.sql(
                                """
                                INSERT INTO processed_event (event_id, consumer, processed_at)
                                VALUES (:eventId, :consumer, :processedAt)
                                ON CONFLICT DO NOTHING
                                """)
                        .param("eventId", event.id())
                        .param("consumer", listener)
                        .param("processedAt", Timestamp.from(clock.instant()))
                        .update()
                == 1;
    }
}
