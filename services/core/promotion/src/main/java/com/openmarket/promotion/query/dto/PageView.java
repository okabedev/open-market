package com.openmarket.promotion.query.dto;

import java.util.List;

/**
 * 쪽 번호 목록(conventions.md "REST API"). 판매자·관리자 쿠폰 관리 화면이 쓴다.
 *
 * @param page 0부터 센다
 */
public record PageView<T>(List<T> items, long totalCount, int page, int size) {}
