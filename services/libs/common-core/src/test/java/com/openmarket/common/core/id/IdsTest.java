package com.openmarket.common.core.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    @DisplayName("UUID v7을 생성 순서대로 정렬되게 만든다")
    void createsTimeOrderedV7() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            ids.add(Ids.newId());
        }

        assertThat(ids).allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).isSortedAccordingTo(UUID::compareTo);
    }
}
