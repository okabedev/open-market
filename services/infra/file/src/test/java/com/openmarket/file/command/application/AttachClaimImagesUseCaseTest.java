package com.openmarket.file.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.file.command.domain.FileOwnerType;
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

/** 클레임 신청의 사진을 클레임에 붙인다(docs/architecture/file.md "클레임 사진"). */
class AttachClaimImagesUseCaseTest {

    static final UUID BUYER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000c1");
    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID CLAIM = UUID.fromString("019a3100-0000-7000-8000-00000000d001");
    static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    final Fakes.StoredFiles files = new Fakes.StoredFiles();
    final Fakes.FileOwners owners = new Fakes.FileOwners();
    final AttachClaimImagesUseCase useCase =
            new AttachClaimImagesUseCase(files, owners, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("구매자가 올린 클레임 사진을 붙이고 소유자에 판매자를 적는다")
    void attaches() {
        UUID first = givenFile(BUYER, FilePurpose.CLAIM_IMAGE);
        UUID second = givenFile(BUYER, FilePurpose.CLAIM_IMAGE);

        useCase.execute(new AttachClaimImagesCommand(CLAIM, BUYER, SELLER, List.of(first, second)));

        assertThat(files.rows.get(first).status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(files.rows.get(second).ownerId()).isEqualTo(CLAIM);
        assertThat(owners.rows.get(CLAIM).ownerType()).isEqualTo(FileOwnerType.CLAIM);
        assertThat(owners.rows.get(CLAIM).sellerId()).isEqualTo(SELLER);
    }

    @Test
    @DisplayName("다른 사람의 파일, 상품 이미지, 없는 파일은 건너뛴다")
    void skips() {
        UUID mine = givenFile(BUYER, FilePurpose.CLAIM_IMAGE);
        UUID others = givenFile(UUID.randomUUID(), FilePurpose.CLAIM_IMAGE);
        UUID product = givenFile(BUYER, FilePurpose.PRODUCT_IMAGE);

        useCase.execute(
                new AttachClaimImagesCommand(
                        CLAIM, BUYER, SELLER, List.of(others, product, UUID.randomUUID(), mine)));

        assertThat(files.rows.get(mine).status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(files.rows.get(others).status()).isEqualTo(StoredFileStatus.TEMP);
        assertThat(files.rows.get(product).status()).isEqualTo(StoredFileStatus.TEMP);
    }

    @Test
    @DisplayName("같은 클레임이 다시 와도 처음 반영한 사진 그대로다. 사진이 없으면 소유자만 남긴다")
    void once() {
        UUID first = givenFile(BUYER, FilePurpose.CLAIM_IMAGE);
        UUID later = givenFile(BUYER, FilePurpose.CLAIM_IMAGE);
        useCase.execute(new AttachClaimImagesCommand(CLAIM, BUYER, SELLER, List.of(first)));

        useCase.execute(new AttachClaimImagesCommand(CLAIM, BUYER, SELLER, List.of(later)));

        assertThat(files.rows.get(first).status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(files.rows.get(later).status()).isEqualTo(StoredFileStatus.TEMP);

        UUID empty = UUID.randomUUID();
        useCase.execute(new AttachClaimImagesCommand(empty, BUYER, SELLER, List.of()));
        assertThat(owners.rows).containsKey(empty);
    }

    private UUID givenFile(UUID uploader, FilePurpose purpose) {
        StoredFile file =
                StoredFile.upload(
                        UUID.randomUUID(),
                        purpose,
                        uploader,
                        ImageFormat.JPEG,
                        100,
                        new ImageDimensions(1000, 1000),
                        NOW);
        files.save(file);
        return file.id();
    }
}
