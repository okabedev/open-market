package com.openmarket.file.command.application;

/**
 * 파일 내용 저장소(ADR 0008). 키는 {@code StoredFile}이 정한 상대 경로다. 지금 구현은 로컬 볼륨이고, S3 호환 저장소로 바꿀 수 있게 이 포트 뒤에
 * 둔다.
 */
public interface FileStoragePort {

    /** 키에 내용을 쓴다. 다 쓴 뒤에야 보이게 한다. 같은 키가 있으면 바꾼다. */
    void write(String key, byte[] content);

    /** 키를 지운다. 없으면 아무것도 하지 않는다. */
    void delete(String key);
}
