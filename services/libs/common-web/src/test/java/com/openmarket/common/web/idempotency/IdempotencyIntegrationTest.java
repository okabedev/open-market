package com.openmarket.common.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.web.testapp.IdempotencyTestController;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(properties = "spring.flyway.locations=classpath:db/common/web")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class IdempotencyIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired IdempotencyTestController controller;

    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("같은 키로 다시 보내면 핸들러를 다시 실행하지 않고 첫 응답을 돌려준다")
    void replaysFirstResponse() throws Exception {
        String key = newKey();
        String item = newItem();

        send("/orders", key, item, "alice")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyInterceptor.REPLAYED_HEADER))
                .andExpect(jsonPath("$.callCount").value(1));
        send("/orders", key, item, "alice")
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyInterceptor.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.item").value(item))
                .andExpect(jsonPath("$.callCount").value(1));

        assertThat(controller.calls(item)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 다른 본문을 보내면 422로 거절한다")
    void rejectsDifferentBodyWithSameKey() throws Exception {
        String key = newKey();
        send("/orders", key, newItem(), "alice").andExpect(status().isCreated());

        send("/orders", key, newItem(), "alice")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("키가 없으면 400으로 거절한다")
    void requiresKey() throws Exception {
        mockMvc.perform(
                        MockMvcRequestBuilders.post("/test/idempotency/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(newItem())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("첫 요청이 처리 중이면 409로 응답한다")
    void conflictWhileInProgress() throws Exception {
        String key = newKey();
        insertInProgress("alice", key, "now()");

        send("/orders", key, newItem(), "alice")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_IDEMPOTENCY_IN_PROGRESS"));
    }

    @Test
    @DisplayName("오래 처리 중으로 남은 키는 중단된 것으로 보고 새 요청이 이어받는다")
    void takesOverStaleInProgress() throws Exception {
        String key = newKey();
        insertInProgress("alice", key, "now() - interval '2 minutes'");

        send("/orders", key, newItem(), "alice").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("5xx 응답은 저장하지 않아 같은 키로 다시 시도할 수 있다")
    void doesNotStoreServerErrors() throws Exception {
        String key = newKey();
        String item = newItem();

        send("/flaky", key, item, "alice").andExpect(status().isInternalServerError());
        send("/flaky", key, item, "alice")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(IdempotencyInterceptor.REPLAYED_HEADER))
                .andExpect(jsonPath("$.callCount").value(2));
    }

    @Test
    @DisplayName("4xx 비즈니스 오류도 첫 응답으로 저장해 그대로 돌려준다")
    void storesClientErrors() throws Exception {
        String key = newKey();
        String item = newItem();

        send("/rejected", key, item, "alice").andExpect(status().is(422));
        send("/rejected", key, item, "alice")
                .andExpect(status().is(422))
                .andExpect(header().string(IdempotencyInterceptor.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.code").value("TEST_OUT_OF_STOCK"));

        assertThat(controller.calls(item)).isEqualTo(1);
    }

    @Test
    @DisplayName("키는 사용자마다 따로다")
    void keysAreScopedPerUser() throws Exception {
        String key = newKey();
        String item = newItem();

        send("/orders", key, item, "alice").andExpect(jsonPath("$.callCount").value(1));
        send("/orders", key, item, "bob")
                .andExpect(header().doesNotExist(IdempotencyInterceptor.REPLAYED_HEADER))
                .andExpect(jsonPath("$.callCount").value(2));
    }

    private ResultActions send(String path, String key, String item, String user) throws Exception {
        MockHttpServletRequestBuilder request =
                MockMvcRequestBuilders.post("/test/idempotency" + path)
                        .principal(() -> user)
                        .header(IdempotencyInterceptor.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(item));
        return mockMvc.perform(request);
    }

    private void insertInProgress(String principal, String key, String createdAtSql) {
        jdbc.sql(
                        "INSERT INTO idempotency_record (principal, idempotency_key, request_hash, status, created_at)"
                                + " VALUES (:principal, :key, :hash, 'IN_PROGRESS', "
                                + createdAtSql
                                + ")")
                .param("principal", principal)
                .param("key", key)
                .param("hash", "0".repeat(64))
                .update();
    }

    private static String body(String item) {
        return "{\"item\": \"" + item + "\"}";
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String newItem() {
        return "item-" + UUID.randomUUID();
    }
}
