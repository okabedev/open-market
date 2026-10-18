package com.openmarket.member.ui.web;

import jakarta.validation.constraints.NotNull;

/**
 * 반려·정지 사유(SEL-2, SEL-3). 감사 로그와 이벤트에 남는다. 개인정보를 적지 않는다.
 *
 * @param reason 500자까지
 */
public record ReasonRequest(@NotNull String reason) {}
