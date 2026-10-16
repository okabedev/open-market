package com.openmarket.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

/** 레포의 infra/config-repo를 그대로 읽어, 서비스가 받을 설정을 확인한다. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class ConfigApplicationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort int port;

    @Test
    @DisplayName("서비스 설정이 공통 설정보다 앞에 온다")
    void servesServiceFileBeforeCommonFile() throws Exception {
        List<String> names =
                propertySources("member").stream().map(s -> (String) s.get("name")).toList();

        assertThat(names).hasSize(2);
        assertThat(names.get(0)).endsWith("member.yml");
        assertThat(names.get(1)).endsWith("application.yml");
    }

    @Test
    @DisplayName("공통 설정에 런타임 기본값이 들어 있다")
    void commonFileHoldsRuntimeDefaults() throws Exception {
        Map<String, Object> common = source(propertySources("member"), "application.yml");

        assertThat(common)
                .containsEntry("spring.threads.virtual.enabled", true)
                .containsEntry("spring.jpa.open-in-view", false)
                .containsEntry("spring.flyway.out-of-order", true)
                .containsEntry("management.otlp.metrics.export.enabled", false)
                .containsEntry("spring.datasource.hikari.schema", "${spring.application.name}");
    }

    @Test
    @DisplayName("비밀 값은 자리 표시자 그대로 내려가고 서비스가 자기 환경 변수로 푼다")
    void passesSecretPlaceholdersThrough() throws Exception {
        Map<String, Object> member = source(propertySources("member"), "member.yml");

        assertThat(member)
                .containsEntry("server.port", 8101)
                .containsEntry("spring.datasource.password", "${MEMBER_DB_PASSWORD}");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> propertySources(String application) throws Exception {
        HttpResponse<String> response =
                HTTP.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://localhost:"
                                                        + port
                                                        + "/"
                                                        + application
                                                        + "/default"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = JSON.readValue(response.body(), Map.class);
        return (List<Map<String, Object>>) body.get("propertySources");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> source(List<Map<String, Object>> sources, String fileName) {
        return sources.stream()
                .filter(s -> ((String) s.get("name")).endsWith(fileName))
                .map(s -> (Map<String, Object>) s.get("source"))
                .findFirst()
                .orElseThrow();
    }
}
