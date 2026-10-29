package com.openmarket.file.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageVariant;
import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileStatus;
import com.openmarket.file.command.domain.UploadPolicy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UploadFileUseCaseTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    final Fakes.StoredFiles files = new Fakes.StoredFiles();
    final Fakes.Storage storage = new Fakes.Storage();
    final Fakes.ImageProcessor images = new Fakes.ImageProcessor();
    final UploadFileUseCase useCase =
            new UploadFileUseCase(files, storage, images, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("원본과 변형을 저장하고 TEMP 파일을 만든다")
    void storesOriginalAndVariants() {
        UploadFileUseCase.UploadedFile uploaded = useCase.execute(command(PNG));

        StoredFile file = files.rows.get(uploaded.fileId());
        assertThat(file.status()).isEqualTo(StoredFileStatus.TEMP);
        assertThat(file.uploaderId()).isEqualTo(SELLER);
        assertThat(file.sizeBytes()).isEqualTo(PNG.length);
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(storage.objects.get(file.storageKey())).isEqualTo(PNG);
        assertThat(storage.objects)
                .containsKeys(
                        file.variantKey(ImageVariant.W300), file.variantKey(ImageVariant.W800));
        assertThat(uploaded.width()).isEqualTo(1200);
        assertThat(uploaded.height()).isEqualTo(800);
        assertThat(uploaded.variants())
                .extracting(UploadFileUseCase.UploadedVariant::name)
                .containsExactly("w300", "w800");
    }

    @Test
    @DisplayName("JPEG, PNG, WebP가 아니면 FILE_UNSUPPORTED_FORMAT이고 아무것도 저장하지 않는다")
    void rejectsUnsupportedFormat() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        command("GIF89a".getBytes(StandardCharsets.US_ASCII))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_UNSUPPORTED_FORMAT");
        assertThat(storage.objects).isEmpty();
        assertThat(files.rows).isEmpty();
    }

    @Test
    @DisplayName("10MB를 넘으면 내용을 읽기 전에 FILE_TOO_LARGE다")
    void rejectsTooLargeBySize() {
        UploadFileCommand command =
                new UploadFileCommand(
                        SELLER,
                        UploadFileCommand.Purpose.PRODUCT_IMAGE,
                        UploadPolicy.MAX_BYTES + 1,
                        () -> {
                            throw new AssertionError("읽으면 안 된다");
                        });

        assertThatThrownBy(() -> useCase.execute(command))
                .hasFieldOrPropertyWithValue("code", "FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("알린 크기보다 실제 내용이 크면 FILE_TOO_LARGE다")
    void rejectsTooLargeContent() {
        byte[] content = Arrays.copyOf(PNG, (int) UploadPolicy.MAX_BYTES + 1);
        UploadFileCommand command =
                new UploadFileCommand(
                        SELLER,
                        UploadFileCommand.Purpose.PRODUCT_IMAGE,
                        PNG.length,
                        () -> new ByteArrayInputStream(content));

        assertThatThrownBy(() -> useCase.execute(command))
                .hasFieldOrPropertyWithValue("code", "FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("픽셀 수가 상한을 넘으면 픽셀을 풀기 전에 FILE_IMAGE_TOO_LARGE다")
    void rejectsTooManyPixels() {
        images.dimensions = new ImageDimensions(10_000, 10_000);

        assertThatThrownBy(() -> useCase.execute(command(PNG)))
                .hasFieldOrPropertyWithValue("code", "FILE_IMAGE_TOO_LARGE");
        assertThat(images.rendered).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    @DisplayName("긴 변이 짧은 변의 10배를 넘으면 픽셀을 풀기 전에 FILE_IMAGE_ASPECT_RATIO다")
    void rejectsElongatedImage() {
        images.dimensions = new ImageDimensions(800, 8001);

        assertThatThrownBy(() -> useCase.execute(command(PNG)))
                .hasFieldOrPropertyWithValue("code", "FILE_IMAGE_ASPECT_RATIO");
        assertThat(images.rendered).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    @DisplayName("긴 변이 짧은 변의 10배인 이미지는 받는다")
    void acceptsAspectRatioAtLimit() {
        images.dimensions = new ImageDimensions(8000, 800);

        useCase.execute(command(PNG));

        assertThat(images.rendered).isNotEmpty();
    }

    @Test
    @DisplayName("형식은 맞지만 읽을 수 없는 이미지는 FILE_INVALID_IMAGE다")
    void rejectsCorruptImage() {
        images.corrupt = true;

        assertThatThrownBy(() -> useCase.execute(command(PNG)))
                .hasFieldOrPropertyWithValue("code", "FILE_INVALID_IMAGE");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    @DisplayName("업로더가 1시간에 올릴 수 있는 수를 넘으면 FILE_UPLOAD_QUOTA_EXCEEDED다")
    void enforcesHourlyQuota() {
        for (int i = 0; i < UploadPolicy.HOURLY_QUOTA; i++) {
            useCase.execute(command(PNG));
        }

        assertThatThrownBy(() -> useCase.execute(command(PNG)))
                .hasFieldOrPropertyWithValue("code", "FILE_UPLOAD_QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("메타데이터를 저장하지 못하면 써 둔 파일을 지운다")
    void deletesWrittenFilesWhenSaveFails() {
        files.failOnSave = true;

        assertThatThrownBy(() -> useCase.execute(command(PNG)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.objects).isEmpty();
    }

    static UploadFileCommand command(byte[] content) {
        return new UploadFileCommand(
                SELLER,
                UploadFileCommand.Purpose.PRODUCT_IMAGE,
                content.length,
                () -> new ByteArrayInputStream(content));
    }
}
