package com.openmarket.file.query.dao;

import static com.openmarket.file.command.domain.QFileOwner.fileOwner;
import static com.openmarket.file.command.domain.QStoredFile.storedFile;

import com.openmarket.file.command.domain.QFileVariant;
import com.openmarket.file.query.dto.FileContentView;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * 변형의 저장 위치와 권한 확인에 쓰는 업로더·소유자. 저장 경로는 커맨드가 기록한 키를 저장소 루트(로컬 볼륨) 아래로 푼다. 저장소를 S3 호환으로 바꾸면 이 DAO도 같이
 * 바꾼다.
 */
@Repository
public class FileContentQueryDao {

    /** 변형은 모두 JPEG이다(커맨드 쪽 {@code ImageVariant.FORMAT}). */
    private static final String VARIANT_CONTENT_TYPE = "image/jpeg";

    private static final StringExpression PURPOSE = storedFile.purpose.stringValue();
    private static final StringExpression OWNER_TYPE = storedFile.ownerType.stringValue();

    private final JPAQueryFactory query;
    private final Path storageRoot;

    public FileContentQueryDao(
            JPAQueryFactory query, @Value("${openmarket.file.storage-root}") Path storageRoot) {
        this.query = query;
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
    }

    /** {@code variant}는 URL의 변형 이름이다(예: {@code w300}). 원본은 찾지 않는다. */
    public Optional<FileContentView> findVariant(UUID fileId, String variant) {
        QFileVariant fileVariant = new QFileVariant("fileVariant");
        Tuple row =
                query.select(
                                fileVariant.storageKey,
                                fileVariant.sizeBytes,
                                PURPOSE,
                                storedFile.uploaderId,
                                OWNER_TYPE,
                                fileOwner.sellerId)
                        .from(storedFile)
                        .join(storedFile.variants, fileVariant)
                        .leftJoin(fileOwner)
                        .on(fileOwner.ownerId.eq(storedFile.ownerId))
                        .where(
                                storedFile.id.eq(fileId),
                                fileVariant
                                        .variant
                                        .stringValue()
                                        .eq(variant.toUpperCase(Locale.ROOT)))
                        .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        String key = Objects.requireNonNull(row.get(fileVariant.storageKey));
        long size = Objects.requireNonNull(row.get(fileVariant.sizeBytes));
        return Optional.of(
                new FileContentView(
                        storageRoot.resolve(key).normalize(),
                        VARIANT_CONTENT_TYPE,
                        size,
                        Objects.requireNonNull(row.get(PURPOSE)),
                        Objects.requireNonNull(row.get(storedFile.uploaderId)),
                        row.get(OWNER_TYPE),
                        row.get(fileOwner.sellerId)));
    }
}
