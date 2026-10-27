package com.openmarket.common.web.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 감사 로그 조회(ADM-4, docs/architecture/data.md "audit_log", ADR 0023). 서비스마다 같은 API이고 admin BFF가 여러
 * 서비스의 결과를 시간순으로 합친다. 보안(ADMIN 역할)은 서비스가 맡는다.
 */
@SpringBootTest(
        properties =
                "spring.flyway.locations=classpath:db/common/web,classpath:db/common/messaging")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class AuditLogQueryIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("기간 안의 기록을 (시각, id) 내림차순으로 주고 커서로 다음 쪽을 넘긴다. 같은 시각이면 id가 큰 것이 먼저다")
    void listsNewestFirstWithCursor() throws Exception {
        String actor = "admin-" + UUID.randomUUID();
        Instant base = Instant.parse("2026-10-20T00:00:00Z");
        UUID first = insert(actor, "COUPON_STOPPED", base);
        UUID sameTimeLow =
                insert(
                        actor,
                        "COUPON_STOPPED",
                        base.plusSeconds(60),
                        uuid("00000000-0000-7000-8000-000000000001"));
        UUID sameTimeHigh =
                insert(
                        actor,
                        "COUPON_STOPPED",
                        base.plusSeconds(60),
                        uuid("00000000-0000-7000-8000-000000000002"));
        insert(actor, "COUPON_STOPPED", base.plusSeconds(3600)); // 기간 밖(to는 포함하지 않는다)

        String page =
                mockMvc.perform(
                                get("/v1/admin/audit-logs")
                                        .param("actorId", actor)
                                        .param("from", base.toString())
                                        .param("to", base.plusSeconds(3600).toString())
                                        .param("size", "2"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.items.length()").value(2))
                        .andExpect(jsonPath("$.items[0].id").value(sameTimeHigh.toString()))
                        .andExpect(jsonPath("$.items[1].id").value(sameTimeLow.toString()))
                        .andExpect(jsonPath("$.items[0].actorId").value(actor))
                        .andExpect(jsonPath("$.items[0].action").value("COUPON_STOPPED"))
                        .andExpect(jsonPath("$.items[0].targetType").value("COUPON"))
                        .andExpect(jsonPath("$.items[0].detail").value("사유"))
                        .andExpect(
                                jsonPath("$.items[0].traceId")
                                        .value("4bf92f3577b34da6a3ce929d0e0e4736"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        mockMvc.perform(
                        get("/v1/admin/audit-logs")
                                .param("actorId", actor)
                                .param("from", base.toString())
                                .param("to", base.plusSeconds(3600).toString())
                                .param("size", "2")
                                .param("before", JsonPath.<String>read(page, "$.nextCursor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("작업으로 거른다")
    void filtersByAction() throws Exception {
        String actor = "admin-" + UUID.randomUUID();
        Instant at = Instant.parse("2026-10-21T00:00:00Z");
        insert(actor, "COUPON_STOPPED", at);
        UUID approved = insert(actor, "SELLER_APPLICATION_APPROVED", at.plusSeconds(1));

        String body =
                mockMvc.perform(
                                get("/v1/admin/audit-logs")
                                        .param("actorId", actor)
                                        .param("action", "SELLER_APPLICATION_APPROVED"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        List<String> ids = JsonPath.read(body, "$.items[*].id");
        assertThat(ids).containsExactly(approved.toString());
    }

    @Test
    @DisplayName("커서는 (시각, id)를 base64url로 감싼 값이라 admin BFF가 합친 목록의 마지막 행으로 만들 수 있다")
    void cursorIsBuiltFromRow() throws Exception {
        String actor = "admin-" + UUID.randomUUID();
        Instant at = Instant.parse("2026-10-22T00:00:00Z");
        UUID older = insert(actor, "COUPON_STOPPED", at);
        UUID newer = insert(actor, "COUPON_STOPPED", at.plusSeconds(1));
        String cursor =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                (at.plusSeconds(1) + "," + newer)
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(get("/v1/admin/audit-logs").param("actorId", actor).param("before", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(older.toString()));
    }

    @Test
    @DisplayName("기간이 거꾸로이거나 크기·커서 형식이 틀리면 400 AUDIT_LOG_INVALID_INPUT")
    void rejectsInvalidInput() throws Exception {
        mockMvc.perform(
                        get("/v1/admin/audit-logs")
                                .param("from", "2026-10-22T00:00:00Z")
                                .param("to", "2026-10-21T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_INVALID_INPUT"));
        mockMvc.perform(get("/v1/admin/audit-logs").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_INVALID_INPUT"));
        mockMvc.perform(get("/v1/admin/audit-logs").param("before", "not-a-cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_LOG_INVALID_INPUT"));
    }

    private UUID insert(String actor, String action, Instant at) {
        return insert(actor, action, at, UUID.randomUUID());
    }

    private UUID insert(String actor, String action, Instant at, UUID id) {
        jdbc.sql(
                        "INSERT INTO audit_log (id, actor_id, action, target_type, target_id, detail, trace_id,"
                                + " occurred_at) VALUES (:id, :actor, :action, 'COUPON', 'c-1', '사유',"
                                + " '4bf92f3577b34da6a3ce929d0e0e4736', :at)")
                .param("id", id)
                .param("actor", actor)
                .param("action", action)
                .param("at", Timestamp.from(at))
                .update();
        return id;
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
