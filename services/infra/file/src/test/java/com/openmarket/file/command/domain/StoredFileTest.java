package com.openmarket.file.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoredFileTest {

    static final UUID ID = UUID.fromString("0199f5a0-0000-7000-8000-000000000001");
    static final UUID UPLOADER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

    @Test
    @DisplayName("확장자가 아니라 내용의 첫 바이트로 JPEG, PNG, WebP를 판별한다")
    void detectsFormatByMagicBytes() {
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)))
                .contains(ImageFormat.JPEG);
        assertThat(ImageFormat.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)))
                .contains(ImageFormat.PNG);
        assertThat(
                        ImageFormat.detect(
                                bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V')))
                .contains(ImageFormat.WEBP);
        assertThat(ImageFormat.detect(bytes('G', 'I', 'F', '8', '9', 'a'))).isEmpty();
        assertThat(ImageFormat.detect("<svg></svg>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageFormat.detect(bytes(0xFF, 0xD8))).isEmpty();
    }

    @Test
    @DisplayName("저장 경로는 사용자 입력 없이 <용도>/<yyyy>/<MM>/<fileId>.<ext>이고, 변형은 같은 디렉터리에 둔다")
    void storageKeys() {
        StoredFile file = upload();

        assertThat(file.storageKey())
                .isEqualTo("product-image/2026/10/0199f5a0-0000-7000-8000-000000000001.png");
        assertThat(file.variantKey(ImageVariant.W300))
                .isEqualTo("product-image/2026/10/0199f5a0-0000-7000-8000-000000000001.w300.jpg");
    }

    @Test
    @DisplayName("업로드한 파일은 TEMP이고 용도에 맞는 변형을 가진다")
    void uploadedFileIsTemp() {
        StoredFile file = upload();
        file.addVariant(ImageVariant.W300, new ImageDimensions(300, 200), 1234);
        file.addVariant(ImageVariant.W800, new ImageDimensions(800, 533), 5678);

        assertThat(file.status()).isEqualTo(StoredFileStatus.TEMP);
        assertThat(FilePurpose.PRODUCT_IMAGE.variants())
                .containsExactly(ImageVariant.W300, ImageVariant.W800);
        assertThat(file.storageKeys())
                .containsExactlyInAnyOrder(
                        file.storageKey(),
                        file.variantKey(ImageVariant.W300),
                        file.variantKey(ImageVariant.W800));
    }

    @Test
    @DisplayName("TEMP 파일은 올린 지 24시간이 지나면 만료된다")
    void tempExpiresAfter24Hours() {
        StoredFile file = upload();

        assertThat(file.isExpiredTemp(NOW.plus(Duration.ofHours(24)).minusSeconds(1))).isFalse();
        assertThat(file.isExpiredTemp(NOW.plus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    @DisplayName("변형 폭은 원본보다 크게 늘리지 않는다")
    void variantWidthDoesNotUpscale() {
        assertThat(ImageVariant.W800.targetWidth(2400)).isEqualTo(800);
        assertThat(ImageVariant.W800.targetWidth(640)).isEqualTo(640);
    }

    static StoredFile upload() {
        return StoredFile.upload(
                ID,
                FilePurpose.PRODUCT_IMAGE,
                UPLOADER,
                ImageFormat.PNG,
                4321,
                new ImageDimensions(1200, 800),
                NOW);
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
