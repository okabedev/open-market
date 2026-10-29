package com.openmarket.member.query.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberContactViewTest {

    @Test
    @DisplayName("toString은 이메일을 남기지 않는다(NFR-7)")
    void toStringHidesEmail() {
        MemberContactView view = new MemberContactView("buyer@example.com", true);

        assertThat(view.toString()).doesNotContain("buyer@example.com").doesNotContain("buyer");
    }
}
