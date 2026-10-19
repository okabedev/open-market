package com.openmarket.file.command.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 업로드 파일 메타데이터(docs/glossary.md "파일"). 원본과 변형은 저장소에 두고 여기에는 위치와 크기만 둔다.
 *
 * <p>저장 경로에는 사용자 입력(파일 이름 등)을 쓰지 않는다. 원본은 {@code <용도>/<yyyy>/<MM>/<fileId>.<ext>}, 변형은 같은 디렉터리의
 * {@code <fileId>.<변형>.jpg}다. 원본은 저장만 하고 제공하지 않는다.
 */
@Entity
@Table(name = "stored_file")
public class StoredFile {

    /** 이 시간이 지나도록 어디에도 붙지 않은 파일은 지운다. */
    public static final Duration TEMP_TTL = Duration.ofHours(24);

    @Id private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FilePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoredFileStatus status;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stored_file_variant", joinColumns = @JoinColumn(name = "file_id"))
    @OrderColumn(name = "position")
    private List<FileVariant> variants = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version private long version;

    protected StoredFile() {}

    private StoredFile(
            UUID id,
            FilePurpose purpose,
            UUID uploaderId,
            ImageFormat format,
            long sizeBytes,
            ImageDimensions dimensions,
            Instant now) {
        this.id = id;
        this.purpose = purpose;
        this.status = StoredFileStatus.TEMP;
        this.uploaderId = uploaderId;
        this.contentType = format.contentType();
        this.sizeBytes = sizeBytes;
        this.width = dimensions.width();
        this.height = dimensions.height();
        this.storageKey = directory(purpose, now) + id + "." + format.extension();
        this.createdAt = now;
    }

    /** 검사를 통과한 업로드를 {@code TEMP}로 만든다. 변형은 {@link #addVariant}로 더한다. */
    public static StoredFile upload(
            UUID id,
            FilePurpose purpose,
            UUID uploaderId,
            ImageFormat format,
            long sizeBytes,
            ImageDimensions dimensions,
            Instant now) {
        return new StoredFile(id, purpose, uploaderId, format, sizeBytes, dimensions, now);
    }

    /** 변형의 저장 경로. 원본과 같은 디렉터리다. */
    public String variantKey(ImageVariant variant) {
        String base = storageKey.substring(0, storageKey.lastIndexOf('.'));
        return base + "." + variant.key() + "." + ImageVariant.FORMAT.extension();
    }

    public void addVariant(ImageVariant variant, ImageDimensions dimensions, long sizeBytes) {
        variants.add(new FileVariant(variant, variantKey(variant), dimensions, sizeBytes));
    }

    /** 만든 지 {@link #TEMP_TTL}이 지난 {@code TEMP}인가. */
    public boolean isExpiredTemp(Instant now) {
        return status == StoredFileStatus.TEMP && !now.isBefore(createdAt.plus(TEMP_TTL));
    }

    /** 지울 때 함께 지울 저장 경로(원본과 변형). */
    public List<String> storageKeys() {
        return Stream.concat(Stream.of(storageKey), variants.stream().map(FileVariant::storageKey))
                .toList();
    }

    private static String directory(FilePurpose purpose, Instant now) {
        ZonedDateTime at = now.atZone(ZoneOffset.UTC);
        return "%s/%04d/%02d/".formatted(purpose.directory(), at.getYear(), at.getMonthValue());
    }

    public UUID id() {
        return id;
    }

    public FilePurpose purpose() {
        return purpose;
    }

    public StoredFileStatus status() {
        return status;
    }

    public UUID uploaderId() {
        return uploaderId;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public ImageDimensions dimensions() {
        return new ImageDimensions(width, height);
    }

    public String storageKey() {
        return storageKey;
    }

    public List<FileVariant> variants() {
        return Collections.unmodifiableList(variants);
    }

    public Instant createdAt() {
        return createdAt;
    }
}
