package com.openmarket.common.messaging.consumer;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.support.KafkaUtils;
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
 *
 * <p>관리자가 DLT 메시지를 다시 보내면 원래 토픽에 {@value #TARGET_GROUP_HEADER} 헤더(실패한 그룹)가 붙어 온다. 지금 리스너의 그룹이 아니면
 * 처리하지 않고 넘긴다. 다른 그룹은 처리 기록(30일 보관)이 지워진 뒤에도 다시 처리하지 않는다(ADR 0022). 리스너 그룹은 리스너 컨테이너가 리스너 스레드에 둔
 * 값이다. 리스너 스레드 밖이라 그룹을 모르면 막지 않는다.
 */
public class IdempotentEventProcessor {

    /** 다시 보낸 DLT 메시지를 처리할 소비자 그룹. */
    public static final String TARGET_GROUP_HEADER = "dlt-target-group";

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
     * @return 핸들러를 실행했으면 {@code true}, 이미 처리한 이벤트이거나 다른 그룹에 다시 보낸 메시지라 건너뛰었으면 {@code false}
     * @throws InvalidEventException 헤더나 페이로드를 읽을 수 없을 때. 영구 오류다
     */
    public <T> boolean process(
            String listener,
            ConsumerRecord<String, String> record,
            Class<T> payloadType,
            Consumer<EventEnvelope<T>> handler) {
        @Nullable String target = targetGroup(record);
        @Nullable String group = KafkaUtils.getConsumerGroupId();
        if (target != null && group != null && !target.equals(group)) {
            log.debug("다른 그룹({})에 다시 보낸 메시지를 건너뛴다: {} ({})", target, record.topic(), listener);
            return false;
        }
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

    private static @Nullable String targetGroup(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader(TARGET_GROUP_HEADER);
        return header == null || header.value() == null
                ? null
                : new String(header.value(), StandardCharsets.UTF_8);
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
