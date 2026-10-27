package com.openmarket.common.messaging.deadletter;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * DLT 메시지 목록 한 쪽.
 *
 * @param nextCursor 다음 쪽을 받을 {@code before} 값. 마지막 쪽이면 비어 있다
 */
public record DeadLetterPage(List<DeadLetterView> items, @Nullable String nextCursor) {}
