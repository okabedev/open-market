package com.openmarket.file.command.application;

import com.openmarket.file.command.domain.AttachOutcome;
import com.openmarket.file.command.domain.FileOwner;
import com.openmarket.file.command.domain.FileOwnerRepository;
import com.openmarket.file.command.domain.FileOwnerType;
import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리뷰 사진을 리뷰에 붙인다(docs/architecture/file.md "리뷰 사진"). 리뷰는 고치지 않으므로 리뷰마다 한 번만 반영하고(소유자 리비전 1), 분리하지
 * 않는다. 붙일 수 없는 파일은 건너뛰고 경고만 남긴다.
 */
@Service
public class AttachReviewImagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(AttachReviewImagesUseCase.class);

    /** 리뷰 소유자의 리비전. 사진은 바뀌지 않는다. */
    static final long REVISION = 1;

    private final StoredFileRepository files;
    private final FileOwnerRepository owners;
    private final Clock clock;

    public AttachReviewImagesUseCase(
            StoredFileRepository files, FileOwnerRepository owners, Clock clock) {
        this.files = files;
        this.owners = owners;
        this.clock = clock;
    }

    @Transactional
    public void execute(AttachReviewImagesCommand command) {
        if (owners.findById(command.reviewId()).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        owners.save(
                FileOwner.create(
                        FileOwnerType.REVIEW,
                        command.reviewId(),
                        command.sellerId(),
                        REVISION,
                        now));
        for (UUID fileId : new LinkedHashSet<>(command.fileIds())) {
            Optional<StoredFile> file = files.findById(fileId);
            if (file.isEmpty()) {
                log.warn("없는 파일이라 붙이지 않는다: fileId={} reviewId={}", fileId, command.reviewId());
                continue;
            }
            AttachOutcome outcome =
                    file.get()
                            .attachTo(
                                    FileOwnerType.REVIEW,
                                    command.reviewId(),
                                    command.buyerId(),
                                    now);
            switch (outcome) {
                case ATTACHED -> files.save(file.get());
                case UNCHANGED -> {}
                case REJECTED ->
                        log.warn(
                                "붙일 수 없는 파일이라 건너뛴다(업로더·용도·소유자 불일치): fileId={} reviewId={}",
                                fileId,
                                command.reviewId());
            }
        }
    }
}
