package com.openmarket.file.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 파일 메타데이터 저장소. 구현은 {@code infra}에 있다. */
public interface StoredFileRepository {

    StoredFile save(StoredFile file);

    Optional<StoredFile> findById(UUID id);

    void delete(StoredFile file);

    /** 업로더가 이 시각 뒤에 올린 파일 수(업로드 한도). */
    long countByUploaderIdAndCreatedAtAfter(UUID uploaderId, Instant after);

    /** 이 시각 전에 만든 {@code TEMP} 파일 ID. 오래된 것부터. */
    List<UUID> findExpiredTempIds(Instant createdBefore, int limit);
}
