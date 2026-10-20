package com.openmarket.order.command.application;

import com.openmarket.order.command.domain.Cart;
import com.openmarket.order.command.domain.CartItemNotFoundException;
import com.openmarket.order.command.domain.CartRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니에 담은 SKU의 수량을 바꾼다(ORD-1). catalog를 부르지 않는다. 판매가 멈춘 상품도 바꿀 수 있다. */
@Service
public class ChangeCartItemQuantityUseCase {

    private final CartRepository carts;
    private final Clock clock;

    public ChangeCartItemQuantityUseCase(CartRepository carts, Clock clock) {
        this.carts = carts;
        this.clock = clock;
    }

    @Transactional
    public void execute(ChangeCartItemQuantityCommand command) {
        Cart cart =
                carts.findForUpdateByMemberId(command.memberId())
                        .orElseThrow(CartItemNotFoundException::new);
        cart.changeQuantity(command.skuId(), command.quantity(), clock.instant());
        carts.save(cart);
    }
}
