package com.openmarket.order.command.application;

import com.openmarket.order.command.application.SkuCatalogPort.CatalogSku;
import com.openmarket.order.command.domain.Cart;
import com.openmarket.order.command.domain.CartItem;
import com.openmarket.order.command.domain.CartRepository;
import com.openmarket.order.command.domain.SkuNotFoundException;
import com.openmarket.order.command.domain.SkuNotOnSaleException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 장바구니에 SKU를 담는다(ORD-1). 판매 중인 SKU만 담는다. catalog 조회는 트랜잭션 밖에서 해서 DB 연결을 잡고 기다리지 않는다. 장바구니는 행을 잠그고
 * 바꾼다.
 */
@Service
public class AddCartItemUseCase {

    private final CartRepository carts;
    private final SkuCatalogPort catalog;
    private final TransactionOperations transaction;
    private final Clock clock;

    public AddCartItemUseCase(
            CartRepository carts,
            SkuCatalogPort catalog,
            TransactionOperations transaction,
            Clock clock) {
        this.carts = carts;
        this.catalog = catalog;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void execute(AddCartItemCommand command) {
        CartItem.checkQuantity(command.quantity());
        CatalogSku sku =
                catalog.findAll(List.of(command.skuId())).stream()
                        .findFirst()
                        .orElseThrow(SkuNotFoundException::new);
        if (!sku.onSale()) {
            throw new SkuNotOnSaleException();
        }
        transaction.executeWithoutResult(
                status -> {
                    carts.createIfAbsent(command.memberId());
                    Cart cart = carts.findForUpdateByMemberId(command.memberId()).orElseThrow();
                    cart.add(command.skuId(), command.quantity(), clock.instant());
                    carts.save(cart);
                });
    }
}
