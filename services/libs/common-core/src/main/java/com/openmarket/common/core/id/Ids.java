package com.openmarket.common.core.id;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/** 엔티티 ID 생성기. 시간 순으로 정렬되는 UUID v7을 만든다. */
public final class Ids {

    private Ids() {}

    public static UUID newId() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
