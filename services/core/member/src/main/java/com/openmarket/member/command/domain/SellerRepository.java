package com.openmarket.member.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 판매자 저장소. 구현은 {@code infra}에 있다. */
public interface SellerRepository {

    boolean existsById(UUID id);

    /** 정지·해제할 때 쓴다. 동시에 바꿔도 이벤트가 상태 변경마다 하나씩만 나가도록 행을 잠근다. */
    Optional<Seller> findForUpdateById(UUID id);

    Seller save(Seller seller);
}
