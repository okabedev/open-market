package com.openmarket.common.test.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSnapshotTest {

    static final String DOCS =
            """
            {"openapi":"3.1.0","servers":[{"url":"http://localhost:54321"}],"paths":{"/v1/ping":{}}}
            """;

    @TempDir Path dir;

    @Test
    @DisplayName("서버 주소를 빼고 들여쓴 JSON으로 저장한다")
    void writesNormalizedDocumentInUpdateMode() throws Exception {
        Path file = dir.resolve("openapi/member.json");

        OpenApiSnapshot.assertMatches("member", DOCS, file, true);

        assertThat(Files.readString(file))
                .doesNotContain("servers")
                .contains("\"openapi\" : \"3.1.0\"")
                .endsWith("}\n");
    }

    @Test
    @DisplayName("저장된 파일과 같으면 통과한다. 서버 주소가 달라도 같은 문서다")
    void passesWhenDocumentIsUnchanged() {
        Path file = dir.resolve("member.json");
        OpenApiSnapshot.assertMatches("member", DOCS, file, true);

        OpenApiSnapshot.assertMatches("member", DOCS.replace("54321", "60000"), file, false);
    }

    @Test
    @DisplayName("API가 바뀌면 갱신 방법을 알려 주며 실패한다")
    void failsWhenDocumentChanged() {
        Path file = dir.resolve("member.json");
        OpenApiSnapshot.assertMatches("member", DOCS, file, true);

        assertThatThrownBy(
                        () ->
                                OpenApiSnapshot.assertMatches(
                                        "member",
                                        DOCS.replace("/v1/ping", "/v1/pong"),
                                        file,
                                        false))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("OPENAPI_UPDATE=true");
    }

    @Test
    @DisplayName("파일이 없으면 만드는 방법을 알려 주며 실패한다")
    void failsWhenSnapshotMissing() {
        assertThatThrownBy(
                        () ->
                                OpenApiSnapshot.assertMatches(
                                        "member", DOCS, dir.resolve("none.json"), false))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("파일이 없다");
    }

    @Test
    @DisplayName("레포 루트에서 clients의 api-client 경로를 찾는다")
    void resolvesSnapshotPathFromRepositoryRoot() {
        assertThat(OpenApiSnapshot.snapshotFile("member"))
                .endsWithRaw(
                        Path.of("clients", "packages", "api-client", "openapi", "member.json"));
    }
}
