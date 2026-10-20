package com.openmarket.order.command.application;

import com.openmarket.order.command.domain.CartRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니에서 SKU를 뺀다(ORD-1). 담지 않은 SKU여도 성공한다. */
@Service
public class RemoveCartItemUseCase {

    private final CartRepository carts;
    private final Clock clock;

    public RemoveCartItemUseCase(CartRepository carts, Clock clock) {
        this.carts = carts;
        this.clock = clock;
    }

    @Transactional
    public void execute(RemoveCartItemCommand command) {
        carts.findForUpdateByMemberId(command.memberId())
                .ifPresent(
                        cart -> {
                            cart.remove(command.skuId(), clock.instant());
                            carts.save(cart);
                        });
    }
}
