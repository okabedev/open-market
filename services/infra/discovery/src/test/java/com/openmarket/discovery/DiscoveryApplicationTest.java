package com.openmarket.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscoveryApplicationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort int port;

    @Test
    @DisplayName("Eureka 레지스트리를 JSON으로 조회할 수 있다")
    void servesRegistry() throws Exception {
        HttpResponse<String> response = get("/eureka/apps", "application/json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"applications\"");
    }

    @Test
    @DisplayName("health와 prometheus 엔드포인트를 노출한다")
    void exposesActuator() throws Exception {
        assertThat(get("/actuator/health", "application/json").body()).contains("\"UP\"");
        assertThat(get("/actuator/prometheus", "text/plain").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", accept)
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
