package com.openmarket.order.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.common.core.error.BusinessException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CartTest {

    static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

    Cart cart = Cart.create(UUID.randomUUID(), NOW);
    UUID sku = UUID.randomUUID();

    @Test
    @DisplayName("담으면 줄이 생기고, 같은 SKU를 다시 담으면 수량을 더하고 담은 시각을 새로 한다")
    void addAccumulates() {
        cart.add(sku, 2, NOW);
        cart.add(sku, 3, NOW.plusSeconds(10));

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().quantity()).isEqualTo(5);
        assertThat(cart.items().getFirst().addedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    @DisplayName("수량은 1~99다. 어기면 ORDER_INVALID_INPUT이다")
    void quantityRange() {
        assertThatThrownBy(() -> cart.add(sku, 0, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        assertThatThrownBy(() -> cart.add(sku, 100, NOW))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        cart.add(sku, 1, NOW);
        assertThatThrownBy(() -> cart.changeQuantity(sku, 0, NOW))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
    }

    @Test
    @DisplayName("다시 담아 99를 넘으면 ORDER_CART_QUANTITY_EXCEEDED이고 수량은 그대로다")
    void accumulatedQuantityLimit() {
        cart.add(sku, 98, NOW);

        assertThatThrownBy(() -> cart.add(sku, 2, NOW))
                .extracting("code")
                .isEqualTo("ORDER_CART_QUANTITY_EXCEEDED");
        assertThat(cart.items().getFirst().quantity()).isEqualTo(98);
    }

    @Test
    @DisplayName("SKU는 50개까지 담는다. 이미 담은 SKU는 50개여도 더할 수 있다")
    void itemLimit() {
        for (int i = 0; i < Cart.MAX_ITEMS; i++) {
            cart.add(i == 0 ? sku : UUID.randomUUID(), 1, NOW);
        }

        assertThatThrownBy(() -> cart.add(UUID.randomUUID(), 1, NOW))
                .extracting("code")
                .isEqualTo("ORDER_CART_FULL");
        cart.add(sku, 1, NOW);
        assertThat(cart.items()).hasSize(Cart.MAX_ITEMS);
    }

    @Test
    @DisplayName("수량 바꾸기는 담은 SKU만 한다. 없으면 ORDER_CART_ITEM_NOT_FOUND다")
    void changeQuantity() {
        cart.add(sku, 1, NOW);
        cart.changeQuantity(sku, 7, NOW);

        assertThat(cart.items().getFirst().quantity()).isEqualTo(7);
        assertThatThrownBy(() -> cart.changeQuantity(UUID.randomUUID(), 1, NOW))
                .extracting("code")
                .isEqualTo("ORDER_CART_ITEM_NOT_FOUND");
    }

    @Test
    @DisplayName("빼기는 담지 않은 SKU여도 아무 일 없이 끝난다")
    void remove() {
        cart.add(sku, 1, NOW);
        cart.remove(UUID.randomUUID(), NOW);
        cart.remove(sku, NOW);

        assertThat(cart.items()).isEmpty();
    }
}
