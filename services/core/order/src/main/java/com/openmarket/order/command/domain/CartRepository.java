package com.openmarket.order.command.domain;

import java.util.Optional;
import java.util.UUID;

/** 장바구니 저장소. 구현은 {@code infra}에 있다. */
public interface CartRepository {

    Cart save(Cart cart);

    /** 장바구니가 없으면 빈 장바구니 행을 만든다. 이미 있으면 아무것도 하지 않는다(동시에 불러도 하나만 생긴다). */
    void createIfAbsent(UUID memberId);

    /** 장바구니 행을 잠그고 읽는다. 같은 회원의 변경이 겹쳐도 덮어쓰지 않는다. */
    Optional<Cart> findForUpdateByMemberId(UUID memberId);
}
