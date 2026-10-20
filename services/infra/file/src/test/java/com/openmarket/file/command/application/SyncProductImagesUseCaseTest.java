package com.openmarket.file.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.file.command.domain.FilePurpose;
import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상품 스냅샷의 이미지 목록으로 첨부를 맞춘다. */
class SyncProductImagesUseCaseTest {

    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID PRODUCT = UUID.fromString("0199f5a0-0000-7000-8000-0000000000b1");
    static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");

    final Fakes.StoredFiles files = new Fakes.StoredFiles();
    final Fakes.FileOwners owners = new Fakes.FileOwners();
    final SyncProductImagesUseCase useCase =
            new SyncProductImagesUseCase(files, owners, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("스냅샷의 이미지를 붙이고, 다음 스냅샷에서 빠진 이미지는 분리한다")
    void attachesAndDetaches() {
        UUID first = givenFile(SELLER);
        UUID second = givenFile(SELLER);

        useCase.execute(new SyncProductImagesCommand(PRODUCT, 1, SELLER, List.of(first, second)));
        assertThat(status(first)).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(status(second)).isEqualTo(StoredFileStatus.ATTACHED);

        useCase.execute(new SyncProductImagesCommand(PRODUCT, 2, SELLER, List.of(second)));
        assertThat(status(first)).isEqualTo(StoredFileStatus.DETACHED);
        assertThat(status(second)).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(owners.rows.get(PRODUCT).revision()).isEqualTo(2);
    }

    @Test
    @DisplayName("반영한 것보다 작거나 같은 revision의 스냅샷은 무시한다")
    void ignoresStaleRevision() {
        UUID first = givenFile(SELLER);
        UUID second = givenFile(SELLER);
        useCase.execute(new SyncProductImagesCommand(PRODUCT, 3, SELLER, List.of(second)));

        useCase.execute(new SyncProductImagesCommand(PRODUCT, 2, SELLER, List.of(first)));
        useCase.execute(new SyncProductImagesCommand(PRODUCT, 3, SELLER, List.of(first)));

        assertThat(status(first)).isEqualTo(StoredFileStatus.TEMP);
        assertThat(status(second)).isEqualTo(StoredFileStatus.ATTACHED);
    }

    @Test
    @DisplayName("다른 사람이 올린 파일과 없는 파일은 건너뛰고 나머지는 붙인다")
    void skipsForeignAndMissingFiles() {
        UUID mine = givenFile(SELLER);
        UUID others = givenFile(UUID.randomUUID());

        useCase.execute(
                new SyncProductImagesCommand(
                        PRODUCT, 1, SELLER, List.of(UUID.randomUUID(), others, mine)));

        assertThat(status(mine)).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(status(others)).isEqualTo(StoredFileStatus.TEMP);
    }

    @Test
    @DisplayName("다른 상품에 붙은 파일은 가져오지 않는다")
    void keepsFileOnOtherProduct() {
        UUID shared = givenFile(SELLER);
        UUID otherProduct = UUID.randomUUID();
        useCase.execute(new SyncProductImagesCommand(otherProduct, 1, SELLER, List.of(shared)));

        useCase.execute(new SyncProductImagesCommand(PRODUCT, 1, SELLER, List.of(shared)));

        assertThat(files.rows.get(shared).ownerId()).isEqualTo(otherProduct);
    }

    private UUID givenFile(UUID uploader) {
        StoredFile file =
                StoredFile.upload(
                        UUID.randomUUID(),
                        FilePurpose.PRODUCT_IMAGE,
                        uploader,
                        ImageFormat.JPEG,
                        100,
                        new ImageDimensions(1000, 1000),
                        NOW);
        files.save(file);
        return file.id();
    }

    private StoredFileStatus status(UUID fileId) {
        return files.rows.get(fileId).status();
    }
}
