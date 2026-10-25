package com.openmarket.promotion.query.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 커서 목록(conventions.md "REST API"). 구매자 내 쿠폰이 쓴다.
 *
 * @param nextCursor 다음 쪽 커서. 마지막 쪽이면 null
 */
public record CursorPageView<T>(List<T> items, long totalCount, @Nullable String nextCursor) {}
