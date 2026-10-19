package com.openmarket.file.command.domain;

/** 파일 상태(docs/glossary.md "파일"). */
public enum StoredFileStatus {
    /** 올렸지만 아직 어디에도 붙지 않았다. 24시간 지나면 지운다. */
    TEMP,
    /** 소유자(상품 등)에 붙었다. */
    ATTACHED,
    /** 소유자에서 빠졌다. 7일 지나면 지운다. */
    DETACHED
}
