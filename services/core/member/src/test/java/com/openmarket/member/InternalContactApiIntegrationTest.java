package com.openmarket.member;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.member.command.application.RegisterMemberCommand;
import com.openmarket.member.command.application.RegisterMemberUseCase;
import com.openmarket.member.command.application.VerifyMemberEmailCommand;
import com.openmarket.member.command.application.VerifyMemberEmailUseCase;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** notification이 부르는 연락처 조회(docs/architecture/member.md "내부 API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class InternalContactApiIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired RegisterMemberUseCase register;

    @Autowired VerifyMemberEmailUseCase verify;

    @Autowired JdbcClient jdbc;

    private UUID member(boolean verified) {
        UUID memberId = UUID.randomUUID();
        register.execute(new RegisterMemberCommand(memberId, memberId + "@example.com", "홍길동"));
        if (verified) {
            verify.execute(new VerifyMemberEmailCommand(memberId));
        }
        return memberId;
    }

    private static String contact(UUID memberId) {
        return "/internal/v1/members/" + memberId + "/contact";
    }

    @Test
    @DisplayName("서비스 간 토큰으로 회원의 이메일과 인증 여부를 준다")
    void returnsContact() throws Exception {
        UUID verified = member(true);
        UUID unverified = member(false);

        mockMvc.perform(get(contact(verified)).with(MemberTokens.internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(verified + "@example.com"))
                .andExpect(jsonPath("$.emailVerified").value(true));
        mockMvc.perform(get(contact(unverified)).with(MemberTokens.internal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    @DisplayName("없거나 탈퇴한 회원은 404 MEMBER_NOT_FOUND다")
    void withdrawnIsNotFound() throws Exception {
        UUID withdrawn = member(true);
        jdbc.sql("UPDATE member SET status = 'WITHDRAWN' WHERE id = :id")
                .param("id", withdrawn)
                .update();

        for (UUID memberId : new UUID[] {withdrawn, UUID.randomUUID()}) {
            mockMvc.perform(get(contact(memberId)).with(MemberTokens.internal()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        }
    }

    @Test
    @DisplayName("사용자 토큰으로는 부를 수 없다(403)")
    void requiresInternalToken() throws Exception {
        UUID memberId = member(true);

        mockMvc.perform(get(contact(memberId)).with(MemberTokens.buyer(memberId)))
                .andExpect(status().isForbidden());
    }
}
