-- 업로드 파일(docs/glossary.md "파일", docs/architecture/file.md). 내용은 저장소(로컬 볼륨)에 있고 여기에는 위치와 크기만 둔다.
CREATE TABLE stored_file (
    id           uuid         PRIMARY KEY,
    purpose      varchar(32)  NOT NULL,
    status       varchar(16)  NOT NULL,
    uploader_id  uuid         NOT NULL,
    content_type varchar(64)  NOT NULL,
    size_bytes   bigint       NOT NULL,
    width        integer      NOT NULL,
    height       integer      NOT NULL,
    storage_key  varchar(255) NOT NULL,
    created_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL
);
-- 업로드 한도(업로더당 1시간)
CREATE INDEX stored_file_uploader_created_idx ON stored_file (uploader_id, created_at);
-- 정리 작업이 오래된 TEMP를 찾는다.
CREATE INDEX stored_file_temp_created_idx ON stored_file (created_at) WHERE status = 'TEMP';

-- 공개 변형(썸네일). 모두 JPEG이다.
CREATE TABLE stored_file_variant (
    file_id     uuid         NOT NULL REFERENCES stored_file (id) ON DELETE CASCADE,
    position    integer      NOT NULL,
    variant     varchar(16)  NOT NULL,
    storage_key varchar(255) NOT NULL,
    width       integer      NOT NULL,
    height      integer      NOT NULL,
    size_bytes  bigint       NOT NULL,
    PRIMARY KEY (file_id, position),
    UNIQUE (file_id, variant)
);
