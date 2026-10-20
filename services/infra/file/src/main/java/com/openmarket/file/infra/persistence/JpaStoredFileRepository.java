package com.openmarket.file.infra.persistence;

import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** {@link StoredFileRepository}의 Spring Data JPA 구현. */
public interface JpaStoredFileRepository
        extends Repository<StoredFile, UUID>, StoredFileRepository {

    @Override
    @Query(
            value =
                    "SELECT id FROM stored_file WHERE (status = 'TEMP' AND created_at <="
                            + " :tempCreatedBefore) OR (status = 'DETACHED' AND detached_at <="
                            + " :detachedBefore) ORDER BY created_at LIMIT :limit",
            nativeQuery = true)
    List<UUID> findExpiredIds(Instant tempCreatedBefore, Instant detachedBefore, int limit);

    @Override
    @Query("SELECT f FROM StoredFile f WHERE f.ownerId = :ownerId AND f.status = 'ATTACHED'")
    List<StoredFile> findAttachedByOwnerId(UUID ownerId);
}
