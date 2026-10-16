package com.openmarket.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.messaging.outbox.Outbox;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/** 서비스 템플릿 확인: 앱이 뜨고, 서비스 스키마에 공통 테이블이 만들어지고, 공통 라이브러리가 그 스키마를 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class MemberApplicationIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Autowired Outbox outbox;

    @Autowired TransactionTemplate transaction;

    @Test
    @DisplayName("Flyway가 서비스 스키마에 공통 테이블을 만든다")
    void createsCommonTablesInServiceSchema() {
        List<String> tables =
                jdbc.sql(
                                "SELECT table_name FROM information_schema.tables WHERE table_schema ="
                                        + " 'member' ORDER BY table_name")
                        .query(String.class)
                        .list();

        assertThat(tables)
                .contains("idempotency_record", "outbox_event", "processed_event", "shedlock");
    }

    @Test
    @DisplayName("outbox 이벤트는 서비스 스키마의 테이블에 쌓인다")
    void outboxWritesToServiceSchema() {
        UUID id =
                transaction.execute(
                        status ->
                                outbox.append(
                                        "member.member",
                                        "member-1",
                                        "TemplateChecked",
                                        Map.of("memberId", "member-1")));

        assertThat(
                        jdbc.sql("SELECT count(*) FROM member.outbox_event WHERE id = :id")
                                .param("id", id)
                                .query(Integer.class)
                                .single())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("health 엔드포인트가 UP을 돌려준다")
    void healthIsUp() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("OpenAPI 문서를 제공한다")
    void exposesOpenApi() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());
    }
}
