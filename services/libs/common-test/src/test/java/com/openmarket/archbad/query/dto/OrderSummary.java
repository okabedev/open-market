package com.openmarket.archbad.query.dto;

import org.springframework.util.StringUtils;

/** 위반: 이름이 View로 끝나지 않고, 허용되지 않은 패키지에 의존한다. */
public record OrderSummary(String id) {
    public boolean hasId() {
        return StringUtils.hasText(id);
    }
}
