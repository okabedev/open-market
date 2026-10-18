package com.openmarket.member.command.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 입점 신청 저장소. 구현은 {@code infra}에 있다. */
public interface SellerApplicationRepository {

    boolean existsByMemberIdAndStatus(UUID memberId, SellerApplicationStatus status);

    /** 심사할 때 쓴다. 관리자 둘이 동시에 심사해도 결과가 하나가 되도록 행을 잠근다. */
    Optional<SellerApplication> findForUpdateById(UUID id);

    /** 탈퇴할 때 그 회원의 신청을 모두 가명 처리한다. */
    List<SellerApplication> findAllByMemberId(UUID memberId);

    SellerApplication save(SellerApplication application);
}
