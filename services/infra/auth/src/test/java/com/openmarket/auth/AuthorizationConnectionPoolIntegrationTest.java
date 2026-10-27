package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 인가 기록을 읽는 요청 하나가 연결을 하나만 쓰는지 확인한다. 인가 기록 행을 읽는 도중에 클라이언트를 조회하며 두 번째 연결을 받으면, 동시 로그인이 풀 크기를 넘을 때
 * 모두가 첫 연결을 쥔 채 기다리다 시간 초과로 500이 된다(docs/architecture/risks.md "부하 측정"). 풀을 1개로 두면 그 상황이 요청 하나로
 * 재현된다.
 */
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.hikari.maximum-pool-size=1",
            "spring.datasource.hikari.connection-timeout=1000"
        })
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class AuthorizationConnectionPoolIntegrationTest {

    static final String VERIFIER = "verifier-0123456789-0123456789-0123456789-0123456789";
    static final String REDIRECT_URI = "http://localhost:3002/auth/callback";

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("연결 풀이 1개여도 로그인, 코드 교환, 토큰 갱신이 된다")
    void authorizationFlowUsesOneConnectionPerRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(
                        post("/login")
                                .session(session)
                                .with(csrf())
                                .param("username", "admin@openmarket.local")
                                .param("password", "admin-test-pass-1"))
                .andExpect(status().is3xxRedirection());

        String redirect =
                mockMvc.perform(
                                get("/oauth2/authorize")
                                        .session(session)
                                        .queryParam("response_type", "code")
                                        .queryParam("client_id", "admin")
                                        .queryParam("scope", "openid profile api")
                                        .queryParam("redirect_uri", REDIRECT_URI)
                                        .queryParam("state", "state-1")
                                        .queryParam("code_challenge", challenge())
                                        .queryParam("code_challenge_method", "S256"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        String code =
                UriComponentsBuilder.fromUriString(redirect)
                        .build()
                        .getQueryParams()
                        .getFirst("code");
        assertThat(code).isNotNull();

        String tokens =
                mockMvc.perform(
                                post("/oauth2/token")
                                        .with(httpBasic("admin", "admin-test-secret"))
                                        .param("grant_type", "authorization_code")
                                        .param("code", code)
                                        .param("redirect_uri", REDIRECT_URI)
                                        .param("code_verifier", VERIFIER))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String refreshToken = JsonPath.read(tokens, "$.refresh_token");

        mockMvc.perform(
                        post("/oauth2/token")
                                .with(httpBasic("admin", "admin-test-secret"))
                                .param("grant_type", "refresh_token")
                                .param("refresh_token", refreshToken))
                .andExpect(status().isOk());
    }

    private static String challenge() throws Exception {
        byte[] digest =
                MessageDigest.getInstance("SHA-256")
                        .digest(VERIFIER.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
