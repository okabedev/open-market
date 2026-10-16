package com.openmarket.common.messaging.outbox;

import java.util.UUID;

/**
 * 발행할 이벤트를 현재 트랜잭션에 쌓는다. 유스케이스는 Kafka로 직접 보내지 않고 이 인터페이스만 쓴다.
 *
 * <p>도메인 변경과 같은 트랜잭션에서 불러야 한다. 커밋되면 Debezium이 읽어 Kafka로 보내고, 롤백되면 이벤트도 사라진다. 트랜잭션 밖에서 부르면 {@link
 * IllegalStateException}을 던진다.
 */
public interface Outbox {

    /** 페이로드 버전 1로 쌓는다. */
    default UUID append(String aggregateType, String aggregateId, String type, Object payload) {
        return append(aggregateType, aggregateId, type, 1, payload);
    }

    /**
     * 이벤트를 쌓는다.
     *
     * @param aggregateType 토픽 이름. 예: {@code order.order}
     * @param aggregateId 집계 ID. Kafka 키가 되어 같은 집계의 이벤트 순서를 지킨다
     * @param type 이벤트 이름. context-map.md의 이름과 같다. 예: {@code OrderPlaced}
     * @param schemaVersion 페이로드 버전. 하위 호환이 깨질 때만 올린다
     * @param payload JSON으로 직렬화할 이벤트 본문
     * @return 이벤트 ID(UUID v7). 소비자의 멱등 키다
     */
    UUID append(
            String aggregateType,
            String aggregateId,
            String type,
            int schemaVersion,
            Object payload);
}
