-- 첨부(docs/architecture/file.md "첨부"). 소유자에서 빠져도 소유자 정보는 남긴다.
ALTER TABLE stored_file
    ADD COLUMN owner_type  varchar(16),
    ADD COLUMN owner_id    uuid,
    ADD COLUMN attached_at timestamptz,
    ADD COLUMN detached_at timestamptz;
-- 소유자에 붙은 파일(분리 대상 찾기)
CREATE INDEX stored_file_owner_idx ON stored_file (owner_id) WHERE status = 'ATTACHED';
-- 정리 작업이 오래된 DETACHED를 찾는다.
CREATE INDEX stored_file_detached_idx ON stored_file (detached_at) WHERE status = 'DETACHED';

-- 파일 소유자(docs/glossary.md "파일 소유자"). 마지막으로 반영한 소유자 이벤트의 리비전.
CREATE TABLE file_owner (
    owner_id   uuid        PRIMARY KEY,
    owner_type varchar(16) NOT NULL,
    revision   bigint      NOT NULL,
    updated_at timestamptz NOT NULL
);
