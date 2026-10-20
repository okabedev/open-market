package com.openmarket.search;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * nori를 넣은 Elasticsearch 컨테이너. 로컬 Compose와 같은 Dockerfile(infra/elasticsearch, ADR 0016)로 이미지를 만들고,
 * Compose처럼 보안을 끈 단일 노드로 띄운다. 테스트에서 {@code @Import}로 쓴다.
 *
 * <p>Testcontainers의 {@code ElasticsearchContainer}는 8 이상에서 TLS와 비밀번호를 켜므로 쓰지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchContainerConfiguration {

    static final Path DOCKERFILE_DIR = Path.of("../../../infra/elasticsearch");
    static final int PORT = 9200;

    @Bean
    GenericContainer<?> elasticsearchContainer() {
        return new GenericContainer<>(
                        new ImageFromDockerfile("openmarket/elasticsearch-test", false)
                                .withFileFromPath(".", DOCKERFILE_DIR))
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("xpack.ml.enabled", "false")
                .withEnv("cluster.routing.allocation.disk.threshold_enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withExposedPorts(PORT)
                .waitingFor(
                        Wait.forHttp("/_cluster/health?wait_for_status=yellow&timeout=2s")
                                .forPort(PORT)
                                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @Bean
    DynamicPropertyRegistrar elasticsearchProperties(GenericContainer<?> elasticsearchContainer) {
        return registry ->
                registry.add(
                        "spring.elasticsearch.uris",
                        () ->
                                "http://"
                                        + elasticsearchContainer.getHost()
                                        + ":"
                                        + elasticsearchContainer.getMappedPort(PORT));
    }
}
