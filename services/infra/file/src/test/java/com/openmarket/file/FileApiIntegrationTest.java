package com.openmarket.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmarket.common.test.container.PostgresContainerConfiguration;
import com.openmarket.common.test.openapi.OpenApiSnapshot;
import com.openmarket.file.infra.FileCleanupJob;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 업로드(판매자), 공개 제공, TEMP 정리, OpenAPI 문서. Kafka 브로커 없이 돈다. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Import(PostgresContainerConfiguration.class)
class FileApiIntegrationTest {

    static final String UPLOAD = "/v1/seller/files";
    static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("openmarket.file.storage-root", STORAGE_ROOT::toString);
    }

    @Autowired MockMvc mockMvc;

    @Autowired JsonMapper json;

    @Autowired JdbcClient jdbc;

    @Autowired FileCleanupJob cleanupJob;

    @Test
    @DisplayName("업로드는 판매자만 한다. 토큰이 없으면 401, 구매자면 403이다")
    void uploadRequiresSeller() throws Exception {
        mockMvc.perform(upload(image("landscape.jpg"))).andExpect(status().isUnauthorized());
        mockMvc.perform(upload(image("landscape.jpg")).with(FileTokens.buyer(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("판매자가 상품 이미지를 올리면 TEMP로 저장하고 변형 URL을 준다. 변형은 토큰 없이 오래 캐시되게 제공한다")
    void uploadsAndServesProductImage() throws Exception {
        UUID seller = UUID.randomUUID();
        JsonNode body =
                readJson(
                        mockMvc.perform(
                                        upload(image("landscape.jpg"))
                                                .with(FileTokens.seller(seller)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.width").value(2400))
                                .andExpect(jsonPath("$.height").value(1600))
                                .andExpect(jsonPath("$.variants[0].name").value("w300"))
                                .andExpect(jsonPath("$.variants[0].width").value(300))
                                .andExpect(jsonPath("$.variants[1].name").value("w800")));
        UUID fileId = UUID.fromString(body.get("fileId").asString());
        assertThat(body.get("variants").get(0).get("url").asString())
                .isEqualTo("/files/" + fileId + "/w300");

        assertThat(
                        jdbc.sql("SELECT status, uploader_id FROM stored_file WHERE id =" + " :id")
                                .param("id", fileId)
                                .query(
                                        (rs, row) ->
                                                rs.getString("status")
                                                        + "/"
                                                        + rs.getString("uploader_id"))
                                .single())
                .isEqualTo("TEMP/" + seller);

        byte[] w300 =
                mockMvc.perform(get("/files/{fileId}/w300", fileId))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "image/jpeg"))
                        .andExpect(
                                header().string(
                                                "Cache-Control",
                                                "max-age=31536000, public, immutable"))
                        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();
        BufferedImage image = Objects.requireNonNull(ImageIO.read(new ByteArrayInputStream(w300)));
        assertThat(image.getWidth()).isEqualTo(300);
    }

    @Test
    @DisplayName("원본이나 없는 변형, 없는 파일은 404다")
    void unknownVariantIsNotFound() throws Exception {
        UUID fileId = uploadAs(UUID.randomUUID(), "sample.webp");

        mockMvc.perform(get("/files/{fileId}/original", fileId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/files/{fileId}/w300", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("확장자를 속인 파일이나 허용하지 않는 형식은 400 FILE_UNSUPPORTED_FORMAT이다")
    void rejectsDisguisedFile() throws Exception {
        expectUploadError(image("not-image.jpg"), 400, "FILE_UNSUPPORTED_FORMAT");
        expectUploadError(image("small.gif"), 400, "FILE_UNSUPPORTED_FORMAT");
    }

    @Test
    @DisplayName("픽셀 수가 너무 많은 이미지는 400 FILE_IMAGE_TOO_LARGE다")
    void rejectsHugeImage() throws Exception {
        expectUploadError(image("huge-dimensions.png"), 400, "FILE_IMAGE_TOO_LARGE");
    }

    @Test
    @DisplayName("10MB를 넘는 내용은 400 FILE_TOO_LARGE다")
    void rejectsOversizedUpload() throws Exception {
        // 실제 서버에서는 multipart 한도(application.yml)가 본문을 다 받기 전에 413으로 끊는다. MockMvc는 multipart를 파싱하지
        // 않으므로 여기서는 유스케이스가 내용을 읽으며 하는 검사를 본다.
        byte[] content = new byte[10 * 1024 * 1024 + 1];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;

        mockMvc.perform(
                        upload(new MockMultipartFile("file", "big.jpg", "image/jpeg", content))
                                .with(FileTokens.seller(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("용도가 없거나 모르는 값이면 400이다")
    void rejectsUnknownPurpose() throws Exception {
        mockMvc.perform(
                        multipart(UPLOAD)
                                .file(image("landscape.jpg"))
                                .param("purpose", "AVATAR")
                                .with(FileTokens.seller(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("24시간 지난 TEMP 파일은 정리 작업이 디스크와 메타데이터에서 지운다")
    void cleansUpExpiredTempFiles() throws Exception {
        UUID expired = uploadAs(UUID.randomUUID(), "landscape.jpg");
        UUID fresh = uploadAs(UUID.randomUUID(), "landscape.jpg");
        jdbc.sql("UPDATE stored_file SET created_at = :at WHERE id = :id")
                .param("at", java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(25))))
                .param("id", expired)
                .update();

        cleanupJob.cleanup();

        mockMvc.perform(get("/files/{fileId}/w300", expired)).andExpect(status().isNotFound());
        mockMvc.perform(get("/files/{fileId}/w300", fresh)).andExpect(status().isOk());
        assertThat(storedNames()).noneMatch(name -> name.startsWith(expired.toString()));
        assertThat(storedNames()).filteredOn(name -> name.startsWith(fresh.toString())).hasSize(3);
    }

    @Test
    @DisplayName("OpenAPI 문서가 프론트 api-client에 저장된 문서와 같다")
    void openApiMatchesClientSnapshot() throws Exception {
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        OpenApiSnapshot.assertMatches("file", apiDocs);
    }

    private UUID uploadAs(UUID seller, String fixture) throws Exception {
        JsonNode body =
                readJson(
                        mockMvc.perform(upload(image(fixture)).with(FileTokens.seller(seller)))
                                .andExpect(status().isCreated()));
        return UUID.fromString(body.get("fileId").asString());
    }

    private void expectUploadError(MockMultipartFile file, int status, String code)
            throws Exception {
        mockMvc.perform(upload(file).with(FileTokens.seller(UUID.randomUUID())))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code));
    }

    private static MockMultipartHttpServletRequestBuilder upload(MockMultipartFile file) {
        MockMultipartHttpServletRequestBuilder request = multipart(UPLOAD).file(file);
        request.param("purpose", "PRODUCT_IMAGE");
        return request;
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static MockMultipartFile image(String name) throws IOException {
        try (InputStream in = FileApiIntegrationTest.class.getResourceAsStream("/images/" + name)) {
            return new MockMultipartFile(
                    "file",
                    name,
                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    Objects.requireNonNull(in, name).readAllBytes());
        }
    }

    private static List<String> storedNames() throws IOException {
        try (Stream<Path> paths = Files.walk(STORAGE_ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("file-storage");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
