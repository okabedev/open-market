package com.openmarket.review.command.domain;

/** 관리자가 리뷰를 숨기거나 다시 보이는 사유(docs/architecture/review.md "숨김"). 앞뒤 공백을 지우고 1~200자다. 감사 로그에 남는다. */
public record ModerationReason(String value) {

    static final int MAX = 200;

    public ModerationReason {
        if (value.isEmpty() || value.length() > MAX || !value.equals(value.strip())) {
            throw new InvalidReviewException("사유는 1~200자로 써 주세요.");
        }
    }

    /** 앞뒤 공백을 지운다. */
    public static ModerationReason of(String value) {
        return new ModerationReason(value.strip());
    }
}
