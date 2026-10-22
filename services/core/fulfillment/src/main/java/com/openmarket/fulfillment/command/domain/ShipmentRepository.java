package com.openmarket.fulfillment.command.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 배송 저장소. 구현은 {@code infra}에 있다. */
public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    /** 하위 주문의 첫 배송({@code OrderPaid}로 만든 것)이 있는가. */
    boolean existsFirstBySellerOrderId(UUID sellerOrderId);

    Optional<Shipment> findById(UUID id);

    /** 배송 행을 잠그고 읽는다(시뮬레이터). */
    Optional<Shipment> findForUpdateById(UUID id);

    /** 하위 주문의 마지막 배송을 잠그고 읽는다(판매자 동작). 교환 재발송이 있으면 그것이다. */
    Optional<Shipment> findLatestForUpdateBySellerOrderId(UUID sellerOrderId);

    /** {@code shippedBefore} 이전에 발송한 {@code SHIPPED} 배송. 오래된 순, 최대 {@code limit}개. */
    List<UUID> findShippedIdsBefore(Instant shippedBefore, int limit);

    /**
     * 자동 구매확정 기한이 {@code now} 이전이고 확정되지 않았고 진행 중인 클레임이 없는 배송 완료 배송. 기한이 오래된 순, 최대 {@code limit}개.
     */
    List<UUID> findConfirmDueIds(Instant now, int limit);

    /** {@code inTransitBefore} 이전에 배송 중이 된 {@code IN_TRANSIT} 배송. 오래된 순, 최대 {@code limit}개. */
    List<UUID> findInTransitIdsBefore(Instant inTransitBefore, int limit);
}
