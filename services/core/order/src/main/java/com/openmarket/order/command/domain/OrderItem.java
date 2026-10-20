package com.openmarket.order.command.domain;

import java.util.List;
import java.util.UUID;

/**
 * 주문할 SKU 하나. catalog에서 확인한 값으로 만든다. 주문 항목({@link OrderLine})의 스냅샷이 된다.
 *
 * @param optionNames 옵션 이름. {@code optionValues}가 이 순서다(최대 2개)
 * @param unitPrice 개당 가격(원)
 */
public record OrderItem(
        UUID skuId,
        UUID productId,
        String productName,
        UUID sellerId,
        String storeName,
        UUID categoryId,
        List<String> optionNames,
        List<String> optionValues,
        long unitPrice,
        int quantity) {}
