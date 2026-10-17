package com.openmarket.common.test.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 서비스 OpenAPI 문서를 프론트 {@code clients/packages/api-client/openapi/<서비스>.json}과 비교한다. 프론트는 이 파일에서 타입을
 * 만든다(clients/CLAUDE.md).
 *
 * <pre>{@code
 * String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
 * OpenApiSnapshot.assertMatches("member", json);
 * }</pre>
 *
 * <p>API를 바꿨으면 환경 변수 {@code OPENAPI_UPDATE=true}로 테스트를 돌려 파일을 다시 쓰고, 프론트에서 타입을 다시 생성한다.
 */
public final class OpenApiSnapshot {

    static final String UPDATE_ENV = "OPENAPI_UPDATE";

    private static final JsonMapper JSON =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private OpenApiSnapshot() {}

    /**
     * 문서가 저장된 파일과 같은지 확인한다. {@code OPENAPI_UPDATE=true}이면 비교하지 않고 파일을 쓴다.
     *
     * @param service 서비스 이름. 파일 이름이 된다
     * @param apiDocs {@code /v3/api-docs} 응답 본문
     */
    public static void assertMatches(String service, String apiDocs) {
        assertMatches(
                service, apiDocs, snapshotFile(service), "true".equals(System.getenv(UPDATE_ENV)));
    }

    static void assertMatches(String service, String apiDocs, Path file, boolean update) {
        String normalized = normalize(apiDocs);
        try {
            if (update) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, normalized);
                return;
            }
            assertThat(file)
                    .as("%s OpenAPI 문서 파일이 없다. %s=true로 테스트를 돌려 만든다", service, UPDATE_ENV)
                    .exists();
            assertThat(Files.readString(file))
                    .as(
                            "%s API가 %s와 다르다. %s=true로 테스트를 돌려 파일을 고치고, clients에서 타입을 다시 생성한다"
                                    + "(pnpm --filter @openmarket/api-client generate)",
                            service, file, UPDATE_ENV)
                    .isEqualTo(normalized);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 실행 환경마다 달라지는 서버 주소를 빼고 들여쓰기를 고정한다. 프론트는 gateway 경로({@code /api/<서비스>}) 아래로 부른다. */
    static String normalize(String apiDocs) {
        ObjectNode document = (ObjectNode) JSON.readTree(apiDocs);
        document.remove("servers");
        return JSON.writeValueAsString(document) + "\n";
    }

    /** 테스트 작업 디렉터리(서비스 모듈)에서 위로 올라가 백엔드·프론트 디렉터리가 함께 있는 레포 루트를 찾는다. */
    static Path snapshotFile(String service) {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("services"))
                    && Files.isDirectory(dir.resolve("clients"))) {
                return dir.resolve(
                        Path.of("clients", "packages", "api-client", "openapi", service + ".json"));
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("레포 루트(services와 clients가 있는 디렉터리)를 찾지 못했다");
    }
}
