package com.openmarket.fulfillment.query.dto;

import java.util.List;

/**
 * 쪽 번호 목록(conventions.md "REST API"). 관리자 중재 목록이 쓴다.
 *
 * @param page 0부터 센다
 */
public record PageView<T>(List<T> items, long totalCount, int page, int size) {}
