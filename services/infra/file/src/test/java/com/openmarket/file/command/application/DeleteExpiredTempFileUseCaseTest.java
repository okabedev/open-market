package com.openmarket.file.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.file.command.domain.FilePurpose;
import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.ImageVariant;
import com.openmarket.file.command.domain.StoredFile;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeleteExpiredTempFileUseCaseTest {

    static final Instant UPLOADED_AT = Instant.parse("2026-10-18T08:00:00Z");

    final Fakes.StoredFiles files = new Fakes.StoredFiles();
    final Fakes.Storage storage = new Fakes.Storage();

    @Test
    @DisplayName("24시간 지난 TEMP 파일은 원본, 변형, 메타데이터를 모두 지운다")
    void deletesExpiredTempFile() {
        StoredFile file = givenFile();

        useCaseAt(UPLOADED_AT.plus(Duration.ofHours(25)))
                .execute(new DeleteExpiredTempFileCommand(file.id()));

        assertThat(files.rows).isEmpty();
        assertThat(storage.objects).isEmpty();
    }

    @Test
    @DisplayName("아직 24시간이 지나지 않았으면 지우지 않는다")
    void keepsFreshTempFile() {
        StoredFile file = givenFile();

        useCaseAt(UPLOADED_AT.plus(Duration.ofHours(23)))
                .execute(new DeleteExpiredTempFileCommand(file.id()));

        assertThat(files.rows).containsKey(file.id());
        assertThat(storage.objects).hasSize(3);
    }

    @Test
    @DisplayName("이미 지운 파일을 다시 지워도 아무 일도 없다")
    void deletingTwiceIsNoop() {
        StoredFile file = givenFile();
        DeleteExpiredTempFileUseCase useCase = useCaseAt(UPLOADED_AT.plus(Duration.ofDays(2)));

        useCase.execute(new DeleteExpiredTempFileCommand(file.id()));
        useCase.execute(new DeleteExpiredTempFileCommand(file.id()));

        assertThat(files.rows).isEmpty();
    }

    @Test
    @DisplayName("저장소에 파일이 이미 없어도 메타데이터를 지운다")
    void deletesRowEvenIfObjectsAreMissing() {
        StoredFile file = givenFile();
        storage.objects.clear();

        useCaseAt(UPLOADED_AT.plus(Duration.ofDays(2)))
                .execute(new DeleteExpiredTempFileCommand(file.id()));

        assertThat(files.rows).isEmpty();
    }

    private StoredFile givenFile() {
        StoredFile file =
                StoredFile.upload(
                        UUID.randomUUID(),
                        FilePurpose.PRODUCT_IMAGE,
                        UUID.randomUUID(),
                        ImageFormat.JPEG,
                        100,
                        new ImageDimensions(1000, 1000),
                        UPLOADED_AT);
        file.addVariant(ImageVariant.W300, new ImageDimensions(300, 300), 10);
        file.addVariant(ImageVariant.W800, new ImageDimensions(800, 800), 20);
        files.save(file);
        file.storageKeys().forEach(key -> storage.write(key, new byte[] {1}));
        return file;
    }

    private DeleteExpiredTempFileUseCase useCaseAt(Instant now) {
        return new DeleteExpiredTempFileUseCase(files, storage, Clock.fixed(now, ZoneOffset.UTC));
    }
}
