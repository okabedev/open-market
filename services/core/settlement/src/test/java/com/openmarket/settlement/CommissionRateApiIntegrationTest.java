package com.openmarket.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 관리자 수수료율 API(docs/architecture/settlement.md "API"). */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class CommissionRateApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;

    final UUID adminId = UUID.randomUUID();

    @Test
    @DisplayName("요율을 PUT하면 204이고 목록에 보인다. 바꾸면 감사 로그가 쌓이고, DELETE하면 기본 요율로 돌아간다")
    void setsAndRemoves() throws Exception {
        UUID category = UUID.randomUUID();

        setRate(category, "{\"rateBp\":850}").andExpect(status().isNoContent());
        setRate(category, "{\"rateBp\":850}").andExpect(status().isNoContent());
        setRate(category, "{\"rateBp\":700}").andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/admin/commission-rates").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultRateBp").value(1000))
                .andExpect(
                        jsonPath("$.rates[?(@.categoryId == '%s')].rateBp", category).value(700));

        mockMvc.perform(delete("/v1/admin/commission-rates/{id}", category).with(admin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/v1/admin/commission-rates/{id}", category).with(admin()))
                .andExpect(status().isNoContent());

        assertThat(
                        jdbc.sql("SELECT count(*) FROM commission_rate WHERE category_id = :id")
                                .param("id", category)
                                .query(Integer.class)
                                .single())
                .isZero();
        assertThat(
                        jdbc.sql(
                                        "SELECT actor_id || '/' || action || '/' || target_type || '/'"
                                                + " || detail FROM audit_log WHERE target_id = :id"
                                                + " ORDER BY occurred_at")
                                .param("id", category.toString())
                                .query(String.class)
                                .list())
                .containsExactly(
                        adminId + "/COMMISSION_RATE_CHANGED/CATEGORY/기본(1000bp) → 850bp",
                        adminId + "/COMMISSION_RATE_CHANGED/CATEGORY/850bp → 700bp",
                        adminId + "/COMMISSION_RATE_CHANGED/CATEGORY/700bp → 기본(1000bp)");
    }

    @Test
    @DisplayName("요율이 범위 밖이거나 없으면 400, 관리자가 아니면 403, 토큰이 없으면 401")
    void validates() throws Exception {
        UUID category = UUID.randomUUID();

        setRate(category, "{\"rateBp\":10001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_INVALID_INPUT"));
        setRate(category, "{}").andExpect(status().isBadRequest());
        mockMvc.perform(
                        put("/v1/admin/commission-rates/{id}", category)
                                .with(seller())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"rateBp\":500}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/admin/commission-rates")).andExpect(status().isUnauthorized());

        assertThat(
                        jdbc.sql("SELECT count(*) FROM commission_rate WHERE category_id = :id")
                                .param("id", category)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    private ResultActions setRate(UUID category, String body) throws Exception {
        return mockMvc.perform(
                put("/v1/admin/commission-rates/{id}", category)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(
                        token -> token.subject(adminId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor seller() {
        return jwt().jwt(
                        token ->
                                token.subject(UUID.randomUUID().toString())
                                        .claim("roles", List.of("BUYER", "SELLER")))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_BUYER"),
                        new SimpleGrantedAuthority("ROLE_SELLER"));
    }
}
