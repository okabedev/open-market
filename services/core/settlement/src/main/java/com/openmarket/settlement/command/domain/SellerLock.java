package com.openmarket.settlement.command.domain;

import java.util.UUID;

/**
 * 판매자 단위 잠금(docs/architecture/settlement.md "정산 대상"). 정산 대상 만들기와 정산 확정이 같은 판매자에 대해 겹치지 않게 한다. 잠금은
 * 트랜잭션이 끝날 때 풀린다. 구현은 {@code infra}에 있다.
 */
public interface SellerLock {

    void lock(UUID sellerId);
}
