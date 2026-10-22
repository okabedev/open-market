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
 * 클레임 사진을 클레임에 붙인다(docs/architecture/file.md "클레임 사진"). 사진은 신청 때 정해지므로 클레임마다 한 번만 반영하고(소유자 리비전 1),
 * 분리하지 않는다. 붙일 수 없는 파일은 건너뛰고 경고만 남긴다.
 */
@Service
public class AttachClaimImagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(AttachClaimImagesUseCase.class);

    /** 클레임 소유자의 리비전. 사진은 바뀌지 않는다. */
    static final long REVISION = 1;

    private final StoredFileRepository files;
    private final FileOwnerRepository owners;
    private final Clock clock;

    public AttachClaimImagesUseCase(
            StoredFileRepository files, FileOwnerRepository owners, Clock clock) {
        this.files = files;
        this.owners = owners;
        this.clock = clock;
    }

    @Transactional
    public void execute(AttachClaimImagesCommand command) {
        if (owners.findById(command.claimId()).isPresent()) {
            return;
        }
        Instant now = clock.instant();
        owners.save(
                FileOwner.create(
                        FileOwnerType.CLAIM, command.claimId(), command.sellerId(), REVISION, now));
        for (UUID fileId : new LinkedHashSet<>(command.fileIds())) {
            Optional<StoredFile> file = files.findById(fileId);
            if (file.isEmpty()) {
                log.warn("없는 파일이라 붙이지 않는다: fileId={} claimId={}", fileId, command.claimId());
                continue;
            }
            AttachOutcome outcome =
                    file.get()
                            .attachTo(
                                    FileOwnerType.CLAIM, command.claimId(), command.buyerId(), now);
            switch (outcome) {
                case ATTACHED -> files.save(file.get());
                case UNCHANGED -> {}
                case REJECTED ->
                        log.warn(
                                "붙일 수 없는 파일이라 건너뛴다(업로더·용도·소유자 불일치): fileId={} claimId={}",
                                fileId,
                                command.claimId());
            }
        }
    }
}
