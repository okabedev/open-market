package com.openmarket.file.command.application;

import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.ImageVariant;
import com.openmarket.file.command.domain.InvalidImageException;
import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 유스케이스 테스트용 가짜 포트. */
final class Fakes {

    private Fakes() {}

    static final class StoredFiles implements StoredFileRepository {

        final Map<UUID, StoredFile> rows = new LinkedHashMap<>();
        boolean failOnSave;

        @Override
        public StoredFile save(StoredFile file) {
            if (failOnSave) {
                throw new IllegalStateException("저장 실패");
            }
            rows.put(file.id(), file);
            return file;
        }

        @Override
        public Optional<StoredFile> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void delete(StoredFile file) {
            rows.remove(file.id());
        }

        @Override
        public long countByUploaderIdAndCreatedAtAfter(UUID uploaderId, Instant after) {
            return rows.values().stream()
                    .filter(file -> file.uploaderId().equals(uploaderId))
                    .filter(file -> file.createdAt().isAfter(after))
                    .count();
        }

        @Override
        public List<UUID> findExpiredTempIds(Instant createdBefore, int limit) {
            return rows.values().stream()
                    .filter(file -> file.isExpiredTemp(createdBefore.plus(StoredFile.TEMP_TTL)))
                    .sorted(Comparator.comparing(StoredFile::createdAt))
                    .limit(limit)
                    .map(StoredFile::id)
                    .toList();
        }
    }

    static final class Storage implements FileStoragePort {

        final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void write(String key, byte[] content) {
            objects.put(key, content.clone());
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    /** 크기는 정해 둔 값을 돌려주고, 변형은 폭만 맞춘 가짜 바이트를 돌려준다. */
    static final class ImageProcessor implements ImageProcessorPort {

        ImageDimensions dimensions = new ImageDimensions(1200, 800);
        boolean corrupt;
        final List<ImageFormat> rendered = new ArrayList<>();

        @Override
        public ImageDimensions probe(byte[] content, ImageFormat format) {
            if (corrupt) {
                throw new InvalidImageException();
            }
            return dimensions;
        }

        @Override
        public List<Rendition> render(
                byte[] content, ImageFormat format, List<ImageVariant> variants) {
            rendered.add(format);
            return variants.stream()
                    .map(
                            variant -> {
                                int width = variant.targetWidth(dimensions.width());
                                int height =
                                        Math.round(
                                                (float) dimensions.height()
                                                        * width
                                                        / dimensions.width());
                                return new Rendition(
                                        variant,
                                        new byte[] {(byte) width},
                                        new ImageDimensions(width, height));
                            })
                    .toList();
        }
    }
}
