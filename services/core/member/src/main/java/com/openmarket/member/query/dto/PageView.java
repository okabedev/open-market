package com.openmarket.member.query.dto;

import java.util.List;

/**
 * 관리자 화면용 쪽 번호 목록(conventions.md "REST API").
 *
 * @param page 0부터 센다
 */
public record PageView<T>(List<T> items, long totalCount, int page, int size) {}
