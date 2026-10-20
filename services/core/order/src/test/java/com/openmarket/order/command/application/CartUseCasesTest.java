package com.openmarket.order.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openmarket.order.command.application.SkuCatalogPort.CatalogSku;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class CartUseCasesTest {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");

    final FakeCartRepository carts = new FakeCartRepository();
    final Map<UUID, CatalogSku> catalog = new HashMap<>();
    final List<Collection<UUID>> catalogCalls = new ArrayList<>();
    final SkuCatalogPort catalogPort =
            ids -> {
                catalogCalls.add(ids);
                return ids.stream().filter(catalog::containsKey).map(catalog::get).toList();
            };
    final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    final TransactionOperations tx = TransactionOperations.withoutTransaction();

    final AddCartItemUseCase add = new AddCartItemUseCase(carts, catalogPort, tx, clock);
    final ChangeCartItemQuantityUseCase change = new ChangeCartItemQuantityUseCase(carts, clock);
    final RemoveCartItemUseCase remove = new RemoveCartItemUseCase(carts, clock);

    final UUID memberId = UUID.randomUUID();
    final UUID sku = UUID.randomUUID();

    @Test
    @DisplayName("판매 중인 SKU는 담긴다. 장바구니가 없으면 만들고 잠가서 바꾼다")
    void addsOnSaleSku() {
        catalog.put(sku, CatalogSkus.sku(sku, true));

        add.execute(new AddCartItemCommand(memberId, sku, 2));

        assertThat(carts.carts.get(memberId).items())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.skuId()).isEqualTo(sku);
                            assertThat(item.quantity()).isEqualTo(2);
                            assertThat(item.addedAt()).isEqualTo(NOW);
                        });
        assertThat(carts.lockedReads).isEqualTo(1);
    }

    @Test
    @DisplayName("catalog에 없는 SKU는 ORDER_SKU_NOT_FOUND, 판매 중이 아니면 ORDER_SKU_NOT_ON_SALE이다")
    void rejectsUnknownOrStoppedSku() {
        assertThatThrownBy(() -> add.execute(new AddCartItemCommand(memberId, sku, 1)))
                .extracting("code")
                .isEqualTo("ORDER_SKU_NOT_FOUND");

        catalog.put(sku, CatalogSkus.sku(sku, false));
        assertThatThrownBy(() -> add.execute(new AddCartItemCommand(memberId, sku, 1)))
                .extracting("code")
                .isEqualTo("ORDER_SKU_NOT_ON_SALE");
        assertThat(carts.carts).isEmpty();
    }

    @Test
    @DisplayName("수량이 틀리면 catalog를 부르기 전에 ORDER_INVALID_INPUT이다")
    void validatesQuantityBeforeCatalog() {
        assertThatThrownBy(() -> add.execute(new AddCartItemCommand(memberId, sku, 0)))
                .extracting("code")
                .isEqualTo("ORDER_INVALID_INPUT");
        assertThat(catalogCalls).isEmpty();
    }

    @Test
    @DisplayName("수량 바꾸기와 빼기는 catalog를 부르지 않는다")
    void changeAndRemoveWithoutCatalog() {
        catalog.put(sku, CatalogSkus.sku(sku, true));
        add.execute(new AddCartItemCommand(memberId, sku, 1));
        catalogCalls.clear();
        catalog.clear();

        change.execute(new ChangeCartItemQuantityCommand(memberId, sku, 5));
        assertThat(carts.carts.get(memberId).items().getFirst().quantity()).isEqualTo(5);

        remove.execute(new RemoveCartItemCommand(memberId, sku));
        remove.execute(new RemoveCartItemCommand(memberId, sku));
        assertThat(carts.carts.get(memberId).items()).isEmpty();
        assertThat(catalogCalls).isEmpty();
    }

    @Test
    @DisplayName("장바구니가 없을 때 수량 바꾸기는 ORDER_CART_ITEM_NOT_FOUND, 빼기는 아무 일 없이 끝난다")
    void noCart() {
        assertThatThrownBy(
                        () -> change.execute(new ChangeCartItemQuantityCommand(memberId, sku, 1)))
                .extracting("code")
                .isEqualTo("ORDER_CART_ITEM_NOT_FOUND");

        remove.execute(new RemoveCartItemCommand(memberId, sku));
        assertThat(carts.carts).isEmpty();
    }
}
