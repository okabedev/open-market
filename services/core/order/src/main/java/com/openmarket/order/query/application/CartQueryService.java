package com.openmarket.order.query.application;

import com.openmarket.order.query.dao.CartQueryDao;
import com.openmarket.order.query.dao.CatalogSkuQueryDao;
import com.openmarket.order.query.dto.CartItemStatus;
import com.openmarket.order.query.dto.CartView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 장바구니 조회(ORD-1). 담은 줄을 읽고 catalog에서 지금 상품 정보를 붙여 판매자별로 묶는다. */
@Service
public class CartQueryService {

    private final CartQueryDao carts;
    private final CatalogSkuQueryDao catalog;

    public CartQueryService(CartQueryDao carts, CatalogSkuQueryDao catalog) {
        this.carts = carts;
        this.catalog = catalog;
    }

    /** DB 조회만 트랜잭션 없이 한다(한 쿼리). catalog 호출 동안 연결을 잡지 않는다. */
    public CartView cart(UUID memberId) {
        List<CartQueryDao.Line> lines = carts.findLines(memberId);
        Map<UUID, CatalogSkuQueryDao.Sku> skus =
                catalog.findAll(lines.stream().map(CartQueryDao.Line::skuId).toList()).stream()
                        .collect(
                                Collectors.toMap(
                                        CatalogSkuQueryDao.Sku::skuId,
                                        Function.identity(),
                                        (first, second) -> first));
        // 줄이 최근 순이라 처음 나온 판매자가 가장 최근에 담은 판매자다.
        Map<UUID, List<CartView.Item>> bySeller = new LinkedHashMap<>();
        Map<UUID, String> storeNames = new LinkedHashMap<>();
        List<UUID> missing = new ArrayList<>();
        for (CartQueryDao.Line line : lines) {
            CatalogSkuQueryDao.Sku sku = skus.get(line.skuId());
            if (sku == null) {
                missing.add(line.skuId());
                continue;
            }
            storeNames.putIfAbsent(sku.sellerId(), sku.storeName());
            bySeller.computeIfAbsent(sku.sellerId(), id -> new ArrayList<>())
                    .add(
                            new CartView.Item(
                                    sku.skuId(),
                                    sku.productId(),
                                    sku.productName(),
                                    sku.optionNames(),
                                    sku.optionValues(),
                                    sku.price(),
                                    line.quantity(),
                                    status(sku, line.quantity()),
                                    sku.thumbnailFileId(),
                                    line.addedAt()));
        }
        List<CartView.SellerGroup> groups =
                bySeller.entrySet().stream()
                        .map(
                                e ->
                                        new CartView.SellerGroup(
                                                e.getKey(),
                                                Objects.requireNonNull(storeNames.get(e.getKey())),
                                                List.copyOf(e.getValue())))
                        .toList();
        return new CartView(groups, List.copyOf(missing));
    }

    static CartItemStatus status(CatalogSkuQueryDao.Sku sku, int quantity) {
        if (!"ON_SALE".equals(sku.productStatus())) {
            return CartItemStatus.NOT_ON_SALE;
        }
        if (sku.availableQuantity() <= 0) {
            return CartItemStatus.SOLD_OUT;
        }
        return sku.availableQuantity() < quantity
                ? CartItemStatus.INSUFFICIENT_STOCK
                : CartItemStatus.AVAILABLE;
    }
}
