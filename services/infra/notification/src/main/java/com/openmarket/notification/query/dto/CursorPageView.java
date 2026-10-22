package com.openmarket.notification.query.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 커서 목록(conventions.md "REST API").
 *
 * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 {@code null}
 */
public record CursorPageView<T>(List<T> items, long totalCount, @Nullable String nextCursor) {}
