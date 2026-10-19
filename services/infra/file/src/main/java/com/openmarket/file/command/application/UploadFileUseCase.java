package com.openmarket.file.command.application;

import com.openmarket.common.core.id.Ids;
import com.openmarket.file.command.domain.FilePurpose;
import com.openmarket.file.command.domain.FileTooLargeException;
import com.openmarket.file.command.domain.FileVariant;
import com.openmarket.file.command.domain.ImageDimensions;
import com.openmarket.file.command.domain.ImageFormat;
import com.openmarket.file.command.domain.StoredFile;
import com.openmarket.file.command.domain.StoredFileRepository;
import com.openmarket.file.command.domain.UnsupportedFileFormatException;
import com.openmarket.file.command.domain.UploadPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 파일을 검사하고 원본과 변형을 저장한 뒤 {@code TEMP}로 기록한다(CAT-1, docs/architecture/file.md).
 *
 * <p>검사 순서는 싼 것부터다: 업로드 한도, 크기, 매직 바이트, 픽셀 수(머리만 읽는다), 그다음 변형을 만든다. 이미지 처리 동안 DB 연결을 잡지 않도록 트랜잭션을
 * 열지 않는다. 메타데이터를 저장하지 못하면 써 둔 파일을 지운다.
 */
@Service
public class UploadFileUseCase {

    private final StoredFileRepository files;
    private final FileStoragePort storage;
    private final ImageProcessorPort images;
    private final Clock clock;

    public UploadFileUseCase(
            StoredFileRepository files,
            FileStoragePort storage,
            ImageProcessorPort images,
            Clock clock) {
        this.files = files;
        this.storage = storage;
        this.images = images;
        this.clock = clock;
    }

    public UploadedFile execute(UploadFileCommand command) {
        Instant now = clock.instant();
        UploadPolicy.checkQuota(
                files.countByUploaderIdAndCreatedAtAfter(
                        command.uploaderId(), now.minus(UploadPolicy.QUOTA_WINDOW)));
        UploadPolicy.checkSize(command.sizeBytes());
        byte[] content = read(command.content());
        ImageFormat format =
                ImageFormat.detect(content).orElseThrow(UnsupportedFileFormatException::new);
        ImageDimensions dimensions = images.probe(content, format);
        UploadPolicy.checkPixels(dimensions);

        FilePurpose purpose = FilePurpose.valueOf(command.purpose().name());
        StoredFile file =
                StoredFile.upload(
                        Ids.newId(),
                        purpose,
                        command.uploaderId(),
                        format,
                        content.length,
                        dimensions,
                        now);
        List<ImageProcessorPort.Rendition> renditions =
                images.render(content, format, purpose.variants());

        List<String> written = new ArrayList<>();
        try {
            storage.write(file.storageKey(), content);
            written.add(file.storageKey());
            for (ImageProcessorPort.Rendition rendition : renditions) {
                String key = file.variantKey(rendition.variant());
                storage.write(key, rendition.jpeg());
                written.add(key);
                file.addVariant(
                        rendition.variant(), rendition.dimensions(), rendition.jpeg().length);
            }
            files.save(file);
        } catch (RuntimeException e) {
            written.forEach(storage::delete);
            throw e;
        }
        return UploadedFile.of(file);
    }

    /** 한도보다 1바이트 더 읽어 본다. 알린 크기를 믿지 않는다. */
    private static byte[] read(UploadFileCommand.Content content) {
        try (InputStream in = content.open()) {
            byte[] bytes = in.readNBytes((int) UploadPolicy.MAX_BYTES + 1);
            if (bytes.length > UploadPolicy.MAX_BYTES) {
                throw new FileTooLargeException();
            }
            return bytes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 올린 파일.
     *
     * @param width EXIF 방향을 반영한 원본 폭
     */
    public record UploadedFile(UUID fileId, int width, int height, List<UploadedVariant> variants) {

        static UploadedFile of(StoredFile file) {
            return new UploadedFile(
                    file.id(),
                    file.dimensions().width(),
                    file.dimensions().height(),
                    file.variants().stream().map(UploadedVariant::of).toList());
        }
    }

    /**
     * 변형 하나.
     *
     * @param name URL에 쓰는 이름. 예: {@code w300}
     */
    public record UploadedVariant(String name, int width, int height) {

        static UploadedVariant of(FileVariant variant) {
            return new UploadedVariant(
                    variant.variant().key(),
                    variant.dimensions().width(),
                    variant.dimensions().height());
        }
    }
}
