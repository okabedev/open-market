-- ShedLock 락 테이블. 여러 인스턴스에서 스케줄 작업이 한 번만 실행되게 한다.
-- 형식은 ShedLock JdbcTemplateLockProvider 문서를 따른다(usingDbTime이 UTC TIMESTAMP를 쓴다).
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp    NOT NULL,
    locked_at  timestamp    NOT NULL,
    locked_by  varchar(255) NOT NULL
);
