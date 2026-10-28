package com.openmarket.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.openmarket.auth.command.domain.Base32;
import com.openmarket.auth.command.domain.Totp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 통합 테스트의 로그인 도우미. 관리자는 비밀번호 다음에 2단계 인증을 거친다(docs/architecture/auth.md "관리자 2단계 인증").
 *
 * <p>테스트끼리 DB를 같이 쓰므로 관리자가 필요한 테스트는 계정을 새로 만들어 등록부터 한다.
 */
final class SecondFactorLogins {

    private SecondFactorLogins() {}

    /** 등록한 관리자의 로그인 세션과 비밀 값, 복구 코드. 테스트 값 묶음이라 레코드끼리 비교하지 않는다. */
    @SuppressWarnings("ArrayRecordComponent")
    record EnrolledAdmin(
            UUID id,
            String email,
            MockHttpSession session,
            byte[] secret,
            List<String> recoveryCodes) {}

    static UUID insertAccount(
            JdbcClient jdbc, PasswordEncoder encoder, String email, String password, String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                        "INSERT INTO account (id, email, password_hash, status, email_verified_at,"
                                + " created_at, updated_at, version) VALUES (:id, :email, :hash,"
                                + " 'ACTIVE', now(), now(), now(), 0)")
                .param("id", id)
                .param("email", email)
                .param("hash", encoder.encode(password))
                .update();
        jdbc.sql("INSERT INTO account_role (account_id, role) VALUES (:id, :role)")
                .param("id", id)
                .param("role", role)
                .update();
        return id;
    }

    /** 비밀번호 로그인. 실패 화면으로 가면 테스트가 실패한다. */
    static MockHttpSession passwordLogin(MockMvc mockMvc, String email, String password)
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String redirect =
                mockMvc.perform(
                                post("/login")
                                        .session(session)
                                        .with(csrf())
                                        .with(newIp())
                                        .param("username", email)
                                        .param("password", password))
                        .andExpect(status().is3xxRedirection())
                        .andReturn()
                        .getResponse()
                        .getRedirectedUrl();
        assertThat(redirect)
                .doesNotContain("error")
                .doesNotContain("locked")
                .doesNotContain("limited");
        return session;
    }

    /** 새 관리자를 만들고 비밀번호로 로그인한 뒤 2단계 인증을 등록한다. 세션은 인가 요청을 할 수 있다. */
    static EnrolledAdmin enrolledAdmin(MockMvc mockMvc, JdbcClient jdbc, PasswordEncoder encoder)
            throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@openmarket.local";
        UUID id = insertAccount(jdbc, encoder, email, "admin-pass-1234", "ADMIN");
        MockHttpSession session = passwordLogin(mockMvc, email, "admin-pass-1234");
        byte[] secret = enrollmentSecret(mockMvc, session);
        List<String> codes = confirmEnrollment(mockMvc, session, code(secret, 0));
        return new EnrolledAdmin(id, email, session, secret, codes);
    }

    /** 등록 화면이 보여 주는 비밀 값. */
    static byte[] enrollmentSecret(MockMvc mockMvc, MockHttpSession session) throws Exception {
        Object secret =
                mockMvc.perform(get("/login/totp").session(session))
                        .andExpect(status().isOk())
                        .andExpect(view().name("totp-enroll"))
                        .andReturn()
                        .getModelAndView()
                        .getModel()
                        .get("secret");
        return Base32.decode((String) Objects.requireNonNull(secret));
    }

    /** 등록 확인. 복구 코드 화면을 돌려받는다. */
    @SuppressWarnings("unchecked")
    static List<String> confirmEnrollment(MockMvc mockMvc, MockHttpSession session, String code)
            throws Exception {
        Object codes =
                mockMvc.perform(
                                post("/login/totp/enroll")
                                        .session(session)
                                        .with(csrf())
                                        .with(newIp())
                                        .param("code", code))
                        .andExpect(status().isOk())
                        .andExpect(view().name("totp-recovery"))
                        .andReturn()
                        .getModelAndView()
                        .getModel()
                        .get("recoveryCodes");
        return (List<String>) Objects.requireNonNull(codes);
    }

    /** 요청마다 다른 주소. 로그인 레이트 리밋(IP 단위)이 테스트끼리 겹치지 않게 한다. */
    static RequestPostProcessor newIp() {
        String address = "10.1." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    /** 지금 단계에서 {@code offset}만큼 뒤 단계의 코드. 등록에 지금 단계를 썼으면 다음 확인은 1을 쓴다(같은 단계는 다시 받지 않는다). */
    static String code(byte[] secret, int offset) {
        return Totp.code(secret, Totp.step(Instant.now()) + offset);
    }
}
