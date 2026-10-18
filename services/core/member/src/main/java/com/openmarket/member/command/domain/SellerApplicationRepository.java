package com.openmarket.member.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 입점 신청 저장소. 구현은 {@code infra}에 있다. */
public interface SellerApplicationRepository {

    boolean existsByMemberIdAndStatus(UUID memberId, SellerApplicationStatus status);

    /** 심사할 때 쓴다. 관리자 둘이 동시에 심사해도 결과가 하나가 되도록 행을 잠근다. */
    Optional<SellerApplication> findForUpdateById(UUID id);

    SellerApplication save(SellerApplication application);
}
