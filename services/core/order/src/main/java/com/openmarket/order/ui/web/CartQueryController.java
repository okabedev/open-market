package com.openmarket.order.ui.web;

import com.openmarket.order.query.application.CartQueryService;
import com.openmarket.order.query.dto.CartView;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 장바구니 조회(ORD-1). 주체는 토큰의 {@code sub}(= memberId)다. */
@RestController
public class CartQueryController {

    private final CartQueryService carts;

    public CartQueryController(CartQueryService carts) {
        this.carts = carts;
    }

    /** 판매자별로 묶은 장바구니. 비어 있어도 200. catalog를 부를 수 없으면 503 {@code ORDER_CATALOG_UNAVAILABLE}. */
    @GetMapping("/v1/cart")
    public CartView cart(Principal principal) {
        return carts.cart(memberId(principal));
    }

    static UUID memberId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
