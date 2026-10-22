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
    static final UUID PRODUCT = UUID.fromString("0199f5a0-0000-7000-8000-0000000000b1");
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

        assertThat(file.isExpired(NOW.plus(Duration.ofHours(24)).minusSeconds(1))).isFalse();
        assertThat(file.isExpired(NOW.plus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    @DisplayName("업로더의 상품에 붙으면 ATTACHED가 되고 만료되지 않는다. 같은 상품에 다시 붙여도 바뀌지 않는다")
    void attachesToOwnersProduct() {
        StoredFile file = upload();

        assertThat(file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, NOW))
                .isEqualTo(AttachOutcome.ATTACHED);
        assertThat(file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, NOW))
                .isEqualTo(AttachOutcome.UNCHANGED);

        assertThat(file.status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(file.ownerId()).isEqualTo(PRODUCT);
        assertThat(file.isExpired(NOW.plus(Duration.ofDays(365)))).isFalse();
    }

    @Test
    @DisplayName("업로더가 다르거나 이미 다른 소유자에 붙은 파일은 붙이지 않는다")
    void rejectsForeignFiles() {
        StoredFile file = upload();

        assertThat(file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UUID.randomUUID(), NOW))
                .isEqualTo(AttachOutcome.REJECTED);
        assertThat(file.status()).isEqualTo(StoredFileStatus.TEMP);

        file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, NOW);
        assertThat(file.attachTo(FileOwnerType.PRODUCT, UUID.randomUUID(), UPLOADER, NOW))
                .isEqualTo(AttachOutcome.REJECTED);
        assertThat(file.ownerId()).isEqualTo(PRODUCT);
    }

    @Test
    @DisplayName("분리하면 DETACHED가 되고 7일 뒤 만료된다. 그 안에 다시 붙일 수 있다")
    void detachesAndReattaches() {
        StoredFile file = upload();
        file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, NOW);
        Instant detachedAt = NOW.plus(Duration.ofDays(30));

        assertThat(file.detach(detachedAt)).isTrue();
        assertThat(file.detach(detachedAt)).isFalse();
        assertThat(file.status()).isEqualTo(StoredFileStatus.DETACHED);
        assertThat(file.isExpired(detachedAt.plus(Duration.ofDays(7)).minusSeconds(1))).isFalse();
        assertThat(file.isExpired(detachedAt.plus(Duration.ofDays(7)))).isTrue();

        assertThat(file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, detachedAt))
                .isEqualTo(AttachOutcome.ATTACHED);
        assertThat(file.isExpired(detachedAt.plus(Duration.ofDays(8)))).isFalse();
    }

    @Test
    @DisplayName("소유자 리비전은 커질 때만 오른다")
    void ownerRevisionOnlyAdvances() {
        FileOwner owner = FileOwner.create(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, 2, NOW);

        assertThat(owner.advance(2, UPLOADER, NOW)).isFalse();
        assertThat(owner.advance(1, UPLOADER, NOW)).isFalse();
        assertThat(owner.advance(3, UPLOADER, NOW)).isTrue();
        assertThat(owner.revision()).isEqualTo(3);
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

    @Test
    @DisplayName("클레임 사진은 비공개이고 claim-image 디렉터리에 둔다. 클레임에만 붙고 상품에는 붙지 않는다")
    void claimImage() {
        StoredFile file =
                StoredFile.upload(
                        ID,
                        FilePurpose.CLAIM_IMAGE,
                        UPLOADER,
                        ImageFormat.JPEG,
                        100,
                        new ImageDimensions(1000, 1000),
                        NOW);

        assertThat(FilePurpose.CLAIM_IMAGE.isPublic()).isFalse();
        assertThat(FilePurpose.PRODUCT_IMAGE.isPublic()).isTrue();
        assertThat(file.storageKey()).startsWith("claim-image/2026/10/");
        assertThat(file.attachTo(FileOwnerType.PRODUCT, PRODUCT, UPLOADER, NOW))
                .isEqualTo(AttachOutcome.REJECTED);
        assertThat(file.attachTo(FileOwnerType.CLAIM, PRODUCT, UPLOADER, NOW))
                .isEqualTo(AttachOutcome.ATTACHED);
    }
}
