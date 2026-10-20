package com.openmarket.file.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 파일 소유자 저장소. 구현은 {@code infra}에 있다. */
public interface FileOwnerRepository {

    FileOwner save(FileOwner owner);

    Optional<FileOwner> findById(UUID ownerId);
}
