package com.openmarket.member.ui.web;

import java.util.UUID;

/**
 * 샘플 이벤트 발행 응답.
 *
 * @param pingId 샘플 ID. 이벤트의 Kafka 키다
 */
record SendPingResponse(UUID pingId) {}
