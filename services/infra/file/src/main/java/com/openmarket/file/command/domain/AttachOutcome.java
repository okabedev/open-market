package com.openmarket.file.command.domain;

/** {@link StoredFile#attachTo}의 결과. */
public enum AttachOutcome {
    /** 새로 붙었다(또는 분리됐다가 다시 붙었다). */
    ATTACHED,
    /** 이미 같은 소유자에 붙어 있다. */
    UNCHANGED,
    /** 붙일 수 없다: 업로더가 다르거나, 용도가 다르거나, 다른 소유자에 붙어 있다. */
    REJECTED
}
