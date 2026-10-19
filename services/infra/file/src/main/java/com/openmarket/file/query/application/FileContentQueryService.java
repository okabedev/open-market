package com.openmarket.file.query.application;

import com.openmarket.common.core.error.NotFoundException;
import com.openmarket.file.query.dao.FileContentQueryDao;
import com.openmarket.file.query.dto.FileContentView;
import java.nio.file.Files;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 이미지 변형 제공. 메타데이터가 없거나 저장소에 내용이 없으면 404 {@code FILE_NOT_FOUND}다. */
@Service
public class FileContentQueryService {

    private static final Logger log = LoggerFactory.getLogger(FileContentQueryService.class);

    private final FileContentQueryDao dao;

    public FileContentQueryService(FileContentQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public FileContentView findVariant(UUID fileId, String variant) {
        FileContentView content = dao.findVariant(fileId, variant).orElseThrow(this::notFound);
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
