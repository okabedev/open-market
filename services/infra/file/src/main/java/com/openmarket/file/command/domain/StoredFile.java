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
import org.jspecify.annotations.Nullable;

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

    /** 소유자에서 빠진 뒤 이 시간이 지나면 지운다. 그 안에는 다시 붙일 수 있다. */
    public static final Duration DETACHED_TTL = Duration.ofDays(7);

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

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type")
    private @Nullable FileOwnerType ownerType;

    @Column(name = "owner_id")
    private @Nullable UUID ownerId;

    @Column(name = "attached_at")
    private @Nullable Instant attachedAt;

    @Column(name = "detached_at")
    private @Nullable Instant detachedAt;

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

    /**
     * 소유자에 붙인다(docs/architecture/file.md "첨부"). {@code TEMP}와 {@code DETACHED}는 붙고, 같은 소유자에 붙어 있으면
     * 그대로다.
     *
     * @param uploaderId 소유자의 주인(상품이면 판매자). 이 파일을 올린 사람이어야 한다
     */
    public AttachOutcome attachTo(
            FileOwnerType ownerType, UUID ownerId, UUID uploaderId, Instant now) {
        if (!this.uploaderId.equals(uploaderId) || purpose != ownerType.purpose()) {
            return AttachOutcome.REJECTED;
        }
        if (status == StoredFileStatus.ATTACHED) {
            return ownerId.equals(this.ownerId) ? AttachOutcome.UNCHANGED : AttachOutcome.REJECTED;
        }
        this.status = StoredFileStatus.ATTACHED;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.attachedAt = now;
        this.detachedAt = null;
        return AttachOutcome.ATTACHED;
    }

    /**
     * 소유자에서 뺀다. 소유자 정보는 남겨 두고 {@link #DETACHED_TTL} 뒤에 지운다.
     *
     * @return 붙어 있다가 빠졌으면 true
     */
    public boolean detach(Instant now) {
        if (status != StoredFileStatus.ATTACHED) {
            return false;
        }
        this.status = StoredFileStatus.DETACHED;
        this.detachedAt = now;
        return true;
    }

    /**
     * 지울 때가 됐는가: 만든 지 {@link #TEMP_TTL}이 지난 {@code TEMP}, 분리된 지 {@link #DETACHED_TTL}이 지난 {@code
     * DETACHED}.
     */
    public boolean isExpired(Instant now) {
        return switch (status) {
            case TEMP -> !now.isBefore(createdAt.plus(TEMP_TTL));
            case DETACHED -> detachedAt != null && !now.isBefore(detachedAt.plus(DETACHED_TTL));
            case ATTACHED -> false;
        };
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

    public @Nullable UUID ownerId() {
        return ownerId;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
