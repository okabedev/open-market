package com.openmarket.common.web.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.web.CommonWebAutoConfiguration;
import com.openmarket.common.web.testapp.ErrorTestController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorTestController.class)
@ImportAutoConfiguration(CommonWebAutoConfiguration.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("BusinessException은 오류 분류에 맞는 상태와 코드로 응답한다")
    void businessException() throws Exception {
        mockMvc.perform(get("/test/errors/business"))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("TEST_RULE_VIOLATED"))
                .andExpect(jsonPath("$.detail").value("규칙을 어겼습니다."));
    }

    @Test
    @DisplayName("BusinessException의 확장 필드는 응답에 실린다. 표준·공통 필드 이름은 덮어쓰지 않는다")
    void businessExceptionProperties() throws Exception {
        mockMvc.perform(get("/test/errors/business-with-properties"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEST_PRICE_CHANGED"))
                .andExpect(jsonPath("$.skuIds[0]").value("sku-1"));
    }

    @Test
    @DisplayName("NotFoundException은 404로 응답한다")
    void notFound() throws Exception {
        mockMvc.perform(get("/test/errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("UNAVAILABLE 분류는 503으로 응답한다")
    void unavailable() throws Exception {
        mockMvc.perform(get("/test/errors/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TEST_DOWNSTREAM_DOWN"));
    }

    @Test
    @DisplayName("TOO_MANY_REQUESTS 분류는 429로 응답한다")
    void tooManyRequests() throws Exception {
        mockMvc.perform(get("/test/errors/too-many"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TEST_QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("처리하지 못한 예외는 500으로 응답하고 내부 메시지를 노출하지 않는다")
    void unexpected() throws Exception {
        mockMvc.perform(get("/test/errors/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("일시적인 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("내부 비밀 정보"))));
    }

    @Test
    @DisplayName("입력 검증 실패는 400과 필드 오류 목록으로 응답한다")
    void validation() throws Exception {
        mockMvc.perform(
                        post("/test/errors/validate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("Spring MVC 표준 오류에도 공통 코드를 붙인다")
    void standardErrors() throws Exception {
        mockMvc.perform(get("/test/errors/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mockMvc.perform(post("/test/errors/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_METHOD_NOT_ALLOWED"));
    }
}
