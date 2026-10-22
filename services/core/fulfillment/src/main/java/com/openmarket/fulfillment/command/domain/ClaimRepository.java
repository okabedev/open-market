package com.openmarket.fulfillment.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 클레임 저장소. 구현은 {@code infra}에 있다. */
public interface ClaimRepository {

    Claim save(Claim claim);

    /** 클레임 행을 잠그고 읽는다(환불 결과 반영). */
    Optional<Claim> findForUpdateById(UUID id);

    /** 하위 주문에 진행 중인 클레임({@code REQUESTED}, {@code APPROVED}, {@code MEDIATING})이 있는가. */
    boolean existsOngoingBySellerOrderId(UUID sellerOrderId);
}
