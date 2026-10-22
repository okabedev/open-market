package com.openmarket.file.query.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.file.command.domain.FilePurpose;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicPurposesTest {

    @Test
    @DisplayName("조회가 공개로 보는 용도는 커맨드 쪽 공개 용도와 같다")
    void samePublicPurposes() {
        assertThat(FileContentQueryService.PUBLIC_PURPOSES)
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(FilePurpose.values())
                                .filter(FilePurpose::isPublic)
                                .map(Enum::name)
                                .toList());
    }
}
