package com.openmarket.catalog.query.dto;

import java.util.UUID;

/** 카테고리 경로의 한 칸(대분류, 소분류). */
public record CategoryRefView(UUID categoryId, String name) {}
