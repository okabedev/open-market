package com.openmarket.order.ui.web;

import static com.openmarket.order.ui.web.CartQueryController.memberId;

import com.openmarket.common.web.idempotency.Idempotent;
import com.openmarket.order.command.application.AddCartItemCommand;
import com.openmarket.order.command.application.AddCartItemUseCase;
import com.openmarket.order.command.application.ChangeCartItemQuantityCommand;
import com.openmarket.order.command.application.ChangeCartItemQuantityUseCase;
import com.openmarket.order.command.application.RemoveCartItemCommand;
import com.openmarket.order.command.application.RemoveCartItemUseCase;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장바구니 담기·수량 바꾸기·빼기(ORD-1). 주체는 토큰의 {@code sub}(= memberId)다. 규칙은 docs/architecture/order.md
 * "장바구니".
 */
@RestController
@RequestMapping("/v1/cart/items")
public class CartCommandController {

    private final AddCartItemUseCase add;
    private final ChangeCartItemQuantityUseCase change;
    private final RemoveCartItemUseCase remove;

    public CartCommandController(
            AddCartItemUseCase add,
            ChangeCartItemQuantityUseCase change,
            RemoveCartItemUseCase remove) {
        this.add = add;
        this.change = change;
        this.remove = remove;
    }

    /**
     * 담는다. 이미 담은 SKU면 수량을 더하므로 {@code Idempotency-Key}가 필요하다. 판매 중이 아니면 422 {@code
     * ORDER_SKU_NOT_ON_SALE}, catalog를 부를 수 없으면 503 {@code ORDER_CATALOG_UNAVAILABLE}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Idempotent
    public void add(Principal principal, @Valid @RequestBody AddCartItemRequest request) {
        add.execute(
                new AddCartItemCommand(memberId(principal), request.skuId(), request.quantity()));
    }

    /** 수량을 바꾼다. 담지 않은 SKU면 404 {@code ORDER_CART_ITEM_NOT_FOUND}. */
    @PutMapping("/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeQuantity(
            Principal principal,
            @PathVariable UUID skuId,
            @Valid @RequestBody ChangeCartItemQuantityRequest request) {
        change.execute(
                new ChangeCartItemQuantityCommand(memberId(principal), skuId, request.quantity()));
    }

    /** 뺀다. 담지 않은 SKU여도 204. */
    @DeleteMapping("/{skuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal principal, @PathVariable UUID skuId) {
        remove.execute(new RemoveCartItemCommand(memberId(principal), skuId));
    }
}
