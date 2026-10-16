package com.openmarket.common.web.idempotency;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/** 멱등성 키의 처리 상태를 저장한다. 키는 요청자(principal)마다 따로다. */
public interface IdempotencyStore {

    /** 키 처리를 시작한다. 처음 보는 키면 처리 중으로 기록하고 {@link Started}를 돌려준다. 오래 처리 중으로 남은 키(서버 중단 등)도 이어받는다. */
    Begin begin(String principal, String key, String requestHash);

    /** 처리 결과를 저장한다. 이후 같은 키 요청에는 이 응답을 돌려준다. */
    void complete(String principal, String key, int status, @Nullable String jsonBody);

    /** 처리 중 기록을 지운다. 5xx나 예외로 끝나 다시 시도할 수 있게 할 때 쓴다. */
    void release(String principal, String key);

    /** 보관 기간이 지난 기록을 지운다. 지운 행 수를 돌려준다. */
    int deleteOlderThan(Duration retention);

    sealed interface Begin {}

    record Started() implements Begin {}

    record InProgress() implements Begin {}

    record Mismatch() implements Begin {}

    record Completed(int status, @Nullable String jsonBody) implements Begin {}
}
