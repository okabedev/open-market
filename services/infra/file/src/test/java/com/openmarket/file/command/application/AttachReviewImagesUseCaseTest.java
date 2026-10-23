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

/** 리뷰 사진을 리뷰에 붙인다(docs/architecture/file.md "리뷰 사진"). */
class AttachReviewImagesUseCaseTest {

    static final UUID BUYER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000c1");
    static final UUID SELLER = UUID.fromString("0199f5a0-0000-7000-8000-0000000000aa");
    static final UUID REVIEW = UUID.fromString("019a3a10-0000-7000-8000-0000000000c1");
    static final Instant NOW = Instant.parse("2026-10-29T01:00:00Z");

    final Fakes.StoredFiles files = new Fakes.StoredFiles();
    final Fakes.FileOwners owners = new Fakes.FileOwners();
    final AttachReviewImagesUseCase useCase =
            new AttachReviewImagesUseCase(files, owners, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("작성자가 올린 리뷰 사진을 붙이고 소유자에 판매자를 적는다")
    void attaches() {
        UUID first = givenFile(BUYER, FilePurpose.REVIEW_IMAGE);
        UUID second = givenFile(BUYER, FilePurpose.REVIEW_IMAGE);

        useCase.execute(
                new AttachReviewImagesCommand(REVIEW, BUYER, SELLER, List.of(first, second)));

        assertThat(files.rows.get(first).status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(files.rows.get(second).ownerId()).isEqualTo(REVIEW);
        assertThat(owners.rows.get(REVIEW).ownerType()).isEqualTo(FileOwnerType.REVIEW);
        assertThat(owners.rows.get(REVIEW).sellerId()).isEqualTo(SELLER);
    }

    @Test
    @DisplayName("다른 사람의 파일, 다른 용도의 파일, 없는 파일은 건너뛴다")
    void skips() {
        UUID mine = givenFile(BUYER, FilePurpose.REVIEW_IMAGE);
        UUID others = givenFile(UUID.randomUUID(), FilePurpose.REVIEW_IMAGE);
        UUID claim = givenFile(BUYER, FilePurpose.CLAIM_IMAGE);

        useCase.execute(
                new AttachReviewImagesCommand(
                        REVIEW, BUYER, SELLER, List.of(others, claim, UUID.randomUUID(), mine)));

        assertThat(files.rows.get(mine).status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(files.rows.get(others).status()).isEqualTo(StoredFileStatus.TEMP);
        assertThat(files.rows.get(claim).status()).isEqualTo(StoredFileStatus.TEMP);
    }

    @Test
    @DisplayName("같은 리뷰가 다시 와도 처음 반영한 사진 그대로다. 사진이 없으면 소유자만 남긴다")
    void once() {
        UUID first = givenFile(BUYER, FilePurpose.REVIEW_IMAGE);
        UUID later = givenFile(BUYER, FilePurpose.REVIEW_IMAGE);
        useCase.execute(new AttachReviewImagesCommand(REVIEW, BUYER, SELLER, List.of(first)));

        useCase.execute(new AttachReviewImagesCommand(REVIEW, BUYER, SELLER, List.of(later)));

        assertThat(files.rows.get(first).status()).isEqualTo(StoredFileStatus.ATTACHED);
        assertThat(files.rows.get(later).status()).isEqualTo(StoredFileStatus.TEMP);

        UUID empty = UUID.randomUUID();
        useCase.execute(new AttachReviewImagesCommand(empty, BUYER, SELLER, List.of()));
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
