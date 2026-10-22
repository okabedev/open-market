package com.openmarket.file.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.file.query.dao.FileContentQueryDao;
import com.openmarket.file.query.dto.FileContentView;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이미지 변형 제공(docs/architecture/file.md "썸네일과 제공", "비공개 파일"). 공개 용도는 누구나, 비공개 용도는 올린 사람, 소유자의 판매자,
 * 관리자만 받는다. 볼 수 없거나, 메타데이터가 없거나, 저장소에 내용이 없으면 모두 404 {@code FILE_NOT_FOUND}다.
 */
@Service
public class FileContentQueryService {

    private static final Logger log = LoggerFactory.getLogger(FileContentQueryService.class);

    /** 공개 용도. 커맨드 쪽 {@code FilePurpose.isPublic()}과 같다(테스트가 확인한다). */
    static final Set<String> PUBLIC_PURPOSES = Set.of("PRODUCT_IMAGE");

    /** 관리자가 볼 수 있는 비공개 파일의 소유자 종류. */
    static final Set<String> ADMIN_OWNER_TYPES = Set.of("CLAIM");

    private final FileContentQueryDao dao;

    public FileContentQueryService(FileContentQueryDao dao) {
        this.dao = dao;
    }

    /** 공개 경로({@code /files/**}). 공개 용도만 준다. */
    @Transactional(readOnly = true)
    public FileContentView findPublicVariant(UUID fileId, String variant) {
        return find(fileId, variant, content -> PUBLIC_PURPOSES.contains(content.purpose()));
    }

    /** 비공개 파일. 올린 사람과 파일이 붙은 소유자의 판매자만 받는다. */
    @Transactional(readOnly = true)
    public FileContentView findPrivateVariant(UUID fileId, String variant, UUID viewerId) {
        return find(
                fileId,
                variant,
                content ->
                        !PUBLIC_PURPOSES.contains(content.purpose())
                                && (viewerId.equals(content.uploaderId())
                                        || viewerId.equals(content.ownerSellerId())));
    }

    /** 관리자. 클레임에 붙은 비공개 파일만 받는다. */
    @Transactional(readOnly = true)
    public FileContentView findVariantForAdmin(UUID fileId, String variant) {
        return find(
                fileId,
                variant,
                content ->
                        !PUBLIC_PURPOSES.contains(content.purpose())
                                && content.ownerType() != null
                                && ADMIN_OWNER_TYPES.contains(content.ownerType()));
    }

    private FileContentView find(UUID fileId, String variant, Predicate<FileContentView> allowed) {
        FileContentView content =
                dao.findVariant(fileId, variant).filter(allowed).orElseThrow(this::notFound);
        if (!Files.isRegularFile(content.path())) {
            // 정리 작업이 내용을 지운 직후이거나 볼륨이 바뀌었다.
            log.warn("파일 내용이 없다: fileId={}, variant={}", fileId, variant);
            throw notFound();
        }
        return content;
    }

    private NotFoundException notFound() {
        return new NotFoundException("FILE_NOT_FOUND", "파일이 없습니다.");
    }
}
