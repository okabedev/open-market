package com.openmarket.file.command.application;

import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 24시간 동안 어디에도 붙지 않은 {@code TEMP} 파일과 분리된 지 7일 지난 {@code DETACHED} 파일을 지운다(CAT-1 AC). 정리 작업이 파일마다
 * 부른다.
 *
 * <p>저장소의 내용을 먼저 지우고 메타데이터를 지운다. 중간에 실패하면 메타데이터가 남아 다음 실행에서 다시 지운다. 이미 없거나 아직 만료되지 않았거나 그 사이 (다시)
 * 첨부됐으면 아무것도 하지 않는다.
 */
@Service
public class DeleteExpiredFileUseCase {

    private final StoredFileRepository files;
    private final FileStoragePort storage;
    private final Clock clock;

    public DeleteExpiredFileUseCase(
            StoredFileRepository files, FileStoragePort storage, Clock clock) {
        this.files = files;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public void execute(DeleteExpiredFileCommand command) {
        Optional<StoredFile> found = files.findById(command.fileId());
        if (found.isEmpty() || !found.get().isExpired(clock.instant())) {
            return;
        }
        StoredFile file = found.get();
        file.storageKeys().forEach(storage::delete);
        files.delete(file);
    }
}
