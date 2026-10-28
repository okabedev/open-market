package com.openmarket.auth.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 2단계 인증 등록 저장소. 구현은 {@code infra}에 있다. */
public interface TotpCredentialRepository {

    /** 코드를 확인할 때 쓴다. 같은 코드가 동시에 와도 한 번만 받도록 행을 잠근다. */
    Optional<TotpCredential> findForUpdateByAccountId(UUID accountId);

    TotpCredential save(TotpCredential credential);
}
