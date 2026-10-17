package com.openmarket.member.command.domain;

import java.util.UUID;

/**
 * 샘플 이벤트(M0). 이벤트 파이프라인과 트레이스 연결을 확인한다. 목록은 docs/architecture/context-map.md의 {@code member.ping}에
 * 있다. M1에서 샘플 API와 함께 지운다.
 *
 * @param pingId 샘플 ID. Kafka 키다
 */
public record PingSent(UUID pingId) {}
