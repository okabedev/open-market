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
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 이미지 첨부를 확정하고 빠진 이미지를 분리한다(docs/architecture/file.md "첨부"). 상품 이벤트마다 부른다. 붙일 수 없는 파일은 건너뛰고 경고만
 * 남긴다(이벤트를 실패로 보지 않는다). 반영한 것보다 오래된 리비전은 무시한다.
 */
@Service
public class SyncProductImagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(SyncProductImagesUseCase.class);

    private final StoredFileRepository files;
    private final FileOwnerRepository owners;
    private final Clock clock;

    public SyncProductImagesUseCase(
            StoredFileRepository files, FileOwnerRepository owners, Clock clock) {
        this.files = files;
        this.owners = owners;
        this.clock = clock;
    }

    @Transactional
    public void execute(SyncProductImagesCommand command) {
        Instant now = clock.instant();
        Optional<FileOwner> found = owners.findById(command.productId());
        if (found.isPresent()
                && !found.get().advance(command.revision(), command.sellerId(), now)) {
            return;
        }
        owners.save(
                found.orElseGet(
                        () ->
                                FileOwner.create(
                                        FileOwnerType.PRODUCT,
                                        command.productId(),
                                        command.sellerId(),
                                        command.revision(),
                                        now)));

        Set<UUID> wanted = new LinkedHashSet<>(command.imageFileIds());
        for (UUID fileId : wanted) {
            Optional<StoredFile> file = files.findById(fileId);
            if (file.isEmpty()) {
                log.warn("없는 파일이라 붙이지 않는다: fileId={} productId={}", fileId, command.productId());
                continue;
            }
            AttachOutcome outcome =
                    file.get()
                            .attachTo(
                                    FileOwnerType.PRODUCT,
                                    command.productId(),
                                    command.sellerId(),
                                    now);
            switch (outcome) {
                case ATTACHED -> files.save(file.get());
                case UNCHANGED -> {}
                case REJECTED ->
                        log.warn(
                                "붙일 수 없는 파일이라 건너뛴다(업로더·용도·소유자 불일치): fileId={} productId={}",
                                fileId,
                                command.productId());
            }
        }
        for (StoredFile attached : files.findAttachedByOwnerId(command.productId())) {
            if (!wanted.contains(attached.id()) && attached.detach(now)) {
                files.save(attached);
            }
        }
    }
}
