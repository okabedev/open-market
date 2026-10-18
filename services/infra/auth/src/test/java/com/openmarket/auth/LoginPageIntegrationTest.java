package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 로그인 화면, 연속 실패 잠금(ACC-1), 로그인 IP 레이트 리밋(NFR-5). 한도는 테스트 설정의 분당 8회다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import({PostgresContainerConfiguration.class, RedisContainerConfiguration.class})
class LoginPageIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("로그인 화면은 한국어이고 가입 링크와 CSRF 토큰 하나가 있다")
    void rendersKoreanLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(Matchers.containsString("<html lang=\"ko\">")))
                .andExpect(content().string(Matchers.containsString("로그인")))
                .andExpect(
                        content().string(Matchers.containsString("http://localhost:3000/signup")))
                .andExpect(
                        content()
                                .string(
                                        Matchers.matchesPattern(
                                                "(?s)(?!.*name=\"_csrf\".*name=\"_csrf\").*name=\"_csrf\".*")));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 같은 안내를 보여 준다(계정이 있는지 알려 주지 않는다)")
    void showsGenericError() throws Exception {
        String email = register();
        loginAs(email, "wrong-password-1", ip()).andExpect(redirectedUrl("/login?error"));
        loginAs("nobody-" + email, "wrong-password-1", ip())
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(get("/login").queryParam("error", ""))
                .andExpect(content().string(Matchers.containsString("이메일 또는 비밀번호가 올바르지 않습니다.")));
    }

    @Test
    @DisplayName("5회 연속 실패하면 맞는 비밀번호로도 15분 동안 로그인할 수 없다")
    void locksAfterFiveFailures() throws Exception {
        String email = register();
        for (int i = 0; i < 5; i++) {
            loginAs(email, "wrong-password-1", ip()).andExpect(redirectedUrl("/login?error"));
        }

        loginAs(email, "password123", ip()).andExpect(redirectedUrl("/login?locked"));

        assertThat(
                        jdbc.sql(
                                        "SELECT locked_until > now() + interval '14 minutes' FROM account WHERE email = :email")
                                .param("email", email)
                                .query(Boolean.class)
                                .single())
                .isTrue();
        mockMvc.perform(get("/login").queryParam("locked", ""))
                .andExpect(content().string(Matchers.containsString("15분")));
    }

    @Test
    @DisplayName("성공하면 실패 횟수를 지운다")
    void successResetsFailures() throws Exception {
        String email = register();
        for (int i = 0; i < 4; i++) {
            loginAs(email, "wrong-password-1", ip());
        }

        loginAs(email, "password123", ip()).andExpect(redirectedUrl("/"));

        assertThat(
                        jdbc.sql("SELECT failed_login_count FROM account WHERE email = :email")
                                .param("email", email)
                                .query(Integer.class)
                                .single())
                .isZero();
    }

    @Test
    @DisplayName("같은 IP에서 분당 한도를 넘으면 로그인을 처리하지 않고 안내로 보낸다. 다른 IP는 영향이 없다")
    void limitsLoginAttemptsPerIp() throws Exception {
        String email = register();
        RequestPostProcessor ip = ip();
        for (int i = 0; i < 8; i++) {
            loginAs("nobody-" + i + email, "wrong-password-1", ip)
                    .andExpect(redirectedUrl("/login?error"));
        }

        loginAs(email, "password123", ip)
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/login?limited"));
        loginAs(email, "password123", ip()).andExpect(redirectedUrl("/"));
        mockMvc.perform(get("/login").queryParam("limited", ""))
                .andExpect(content().string(Matchers.containsString("잠시 뒤")));
    }

    private ResultActions loginAs(String email, String password, RequestPostProcessor ip)
            throws Exception {
        return mockMvc.perform(
                post("/login")
                        .with(csrf())
                        .with(ip)
                        .param("username", email)
                        .param("password", password));
    }

    private String register() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(
                        post("/v1/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + email
                                                + "\",\"password\":\"password123\",\"name\":\"홍길동\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    /** 테스트마다 다른 IP. 레이트 리밋 창이 테스트 사이에 섞이지 않게 한다. */
    private static RequestPostProcessor ip() {
        String address = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
