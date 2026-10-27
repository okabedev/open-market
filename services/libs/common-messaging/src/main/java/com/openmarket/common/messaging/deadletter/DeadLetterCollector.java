package com.openmarket.common.messaging.deadletter;

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 이 서비스의 DLT 메시지를 {@code dead_letter}에 모은다(ADR 0022, docs/architecture/messaging.md "DLT 재처리").
 *
 * <p>기동할 때 리스너 등록 정보에서 이 서비스의 리스너 그룹과 구독 토픽을 얻는다. 그 토픽들의 DLT({@code <토픽>.DLT})를 그룹 {@code
 * <서비스>-dead-letter}로 읽고, 실패한 그룹({@code kafka_dlt-original-consumer-group})이 이 서비스의 리스너 그룹인 메시지만
 * 넣는다. DLT는 토픽을 읽는 모든 그룹이 함께 쓰므로 다른 서비스의 실패도 섞여 있다.
 *
 * <p>넣지 못하면(DB 장애) 오프셋을 넘기지 않고 5초마다 다시 시도한다. DLT 메시지를 잃지 않는다.
 */
public class DeadLetterCollector implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterCollector.class);

    static final String DLT_SUFFIX = ".DLT";
    static final String GROUP_SUFFIX = "-dead-letter";
    static final long RETRY_INTERVAL_MS = 5_000;

    private final KafkaListenerEndpointRegistry registry;
    private final ConsumerFactory<String, String> consumerFactory;
    private final DeadLetterRecorder recorder;
    private final String serviceName;
    private final boolean autoStartup;

    private @Nullable ConcurrentMessageListenerContainer<String, String> container;

    public DeadLetterCollector(
            KafkaListenerEndpointRegistry registry,
            ConsumerFactory<String, String> consumerFactory,
            DeadLetterRecorder recorder,
            String serviceName,
            boolean autoStartup) {
        this.registry = registry;
        this.consumerFactory = consumerFactory;
        this.recorder = recorder;
        this.serviceName = serviceName;
        this.autoStartup = autoStartup;
    }

    @Override
    public void start() {
        Set<String> groups = new LinkedHashSet<>();
        Set<String> deadLetterTopics = new LinkedHashSet<>();
        for (MessageListenerContainer listener : registry.getListenerContainers()) {
            String[] topics = listener.getContainerProperties().getTopics();
            String group = listener.getGroupId();
            if (group == null || topics == null) {
                continue;
            }
            for (String topic : topics) {
                // DLT를 직접 읽는 리스너(테스트 등)는 수집 대상이 아니다.
                if (!topic.endsWith(DLT_SUFFIX)) {
                    groups.add(group);
                    deadLetterTopics.add(topic + DLT_SUFFIX);
                }
            }
        }
        if (deadLetterTopics.isEmpty()) {
            log.info("수집할 DLT가 없다(리스너 없음)");
            return;
        }
        ContainerProperties properties =
                new ContainerProperties(deadLetterTopics.toArray(String[]::new));
        properties.setGroupId(serviceName + GROUP_SUFFIX);
        properties.setMessageListener(
                (MessageListener<String, String>) record -> collect(record, groups));
        ConcurrentMessageListenerContainer<String, String> created =
                new ConcurrentMessageListenerContainer<>(consumerFactory, properties);
        created.setBeanName(serviceName + "-dead-letter-collector");
        created.setCommonErrorHandler(
                new DefaultErrorHandler(
                        new FixedBackOff(RETRY_INTERVAL_MS, FixedBackOff.UNLIMITED_ATTEMPTS)));
        created.start();
        container = created;
        log.info("DLT 수집을 시작한다: 그룹 {}, 토픽 {}", groups, deadLetterTopics);
    }

    private void collect(ConsumerRecord<String, String> record, Set<String> groups) {
        String group = DeadLetterRecorder.originalGroup(record);
        if (group == null || !groups.contains(group)) {
            return;
        }
        boolean recorded;
        try {
            recorded = recorder.record(record);
        } catch (RuntimeException e) {
            // 오류 처리기는 같은 메시지를 다시 시도할 뿐 남기지 않는다. 계속 실패하면 여기서 보인다.
            log.warn(
                    "DLT 메시지를 넣지 못해 다시 시도한다: {}-{}@{}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    e);
            throw e;
        }
        if (recorded) {
            log.warn(
                    "DLT 메시지를 모았다: {}-{}@{} (그룹 {})",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    group);
        }
    }

    @Override
    public void stop() {
        ConcurrentMessageListenerContainer<String, String> running = container;
        if (running != null) {
            running.stop();
            container = null;
        }
    }

    @Override
    public boolean isRunning() {
        return container != null;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    /** 리스너 컨테이너({@code Integer.MAX_VALUE - 100})보다 늦게 시작하고 먼저 멈춘다. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 50;
    }
}
