package com.openmarket.gateway;

import com.openmarket.common.test.container.RedisContainerConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** gateway를 실제 포트로 띄우고, 뒤의 서비스(auth, member)와 JWK 엔드포인트를 {@link TestUpstream}으로 대신한다. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(RedisContainerConfiguration.class)
abstract class GatewayTestSupport {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    static final TestUpstream upstream = TestUpstream.INSTANCE;

    @LocalServerPort int port;

    @DynamicPropertySource
    static void upstreamProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.discovery.client.simple.instances.member[0].uri", upstream::baseUrl);
        registry.add(
                "spring.cloud.discovery.client.simple.instances.auth[0].uri", upstream::baseUrl);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> upstream.baseUrl() + "/oauth2/jwks");
    }

    HttpResponse<String> get(String path) throws Exception {
        return get(path, Map.of());
    }

    HttpResponse<String> getWithToken(String path, String token) throws Exception {
        return get(path, Map.of("Authorization", "Bearer " + token));
    }

    HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
        return send(port, path, headers);
    }

    HttpResponse<String> post(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> send(int port, String path, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        headers.forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
