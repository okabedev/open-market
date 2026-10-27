-- 장애 주입 정합성 검사(R16). infra/fault/run.sh가 장애를 풀고 결제 기한·재조회·보상 취소가 돌 시간을 기다린 뒤 슈퍼유저로 실행한다.
-- 서비스 스키마를 가로질러 읽는 것은 이 검사뿐이다(서비스는 다른 스키마를 읽지 않는다).
-- 마지막 표의 위반 수가 모두 0이어야 한다. 앞의 표는 참고용 분포다.
\pset footer off

\echo '## 주문 상태'
SELECT status, cancel_reason, count(*) FROM "order".orders GROUP BY 1, 2 ORDER BY 1, 2;

\echo '## 결제 상태'
SELECT status, count(*) FROM payment.payment GROUP BY 1 ORDER BY 1;

\echo '## 재고 예약 상태'
SELECT status, count(*) FROM catalog.stock_reservation GROUP BY 1 ORDER BY 1;

\echo '## 위반(모두 0이어야 한다)'
SELECT check_name, violations FROM (
  -- 끝나지 않은 주문. 결제 기한이 지났으므로 결제 완료나 취소여야 한다.
  SELECT 1 AS n, 'orders_not_final' AS check_name,
         (SELECT count(*) FROM "order".orders WHERE status NOT IN ('PAID', 'CANCELLED')) AS violations
  UNION ALL
  -- 결제 완료 주문은 승인된 결제가 있다.
  SELECT 2, 'paid_without_approved_payment',
         (SELECT count(*) FROM "order".orders o
           WHERE o.status = 'PAID'
             AND NOT EXISTS (SELECT 1 FROM payment.payment p
                              WHERE p.order_id = o.id AND p.status IN ('APPROVED', 'PARTIALLY_CANCELLED')))
  UNION ALL
  -- 승인된 결제는 결제 완료 주문의 것이다. 취소된 주문의 승인은 보상 취소로 CANCELLED가 돼야 한다.
  SELECT 3, 'approved_payment_without_paid_order',
         (SELECT count(*) FROM payment.payment p
           WHERE p.status IN ('APPROVED', 'PARTIALLY_CANCELLED')
             AND NOT EXISTS (SELECT 1 FROM "order".orders o WHERE o.id = p.order_id AND o.status = 'PAID'))
  UNION ALL
  -- 정해지지 않은 결제. 재조회 작업이 IN_PROGRESS를 정하고, 취소된 주문의 READY 결제는 취소된다.
  SELECT 4, 'payment_not_final',
         (SELECT count(*) FROM payment.payment p
           WHERE p.status = 'IN_PROGRESS'
              OR (p.status = 'READY'
                  AND EXISTS (SELECT 1 FROM "order".orders o WHERE o.id = p.order_id AND o.status = 'CANCELLED')))
  UNION ALL
  -- 결제 완료 주문의 재고 예약은 확정됐다.
  SELECT 5, 'paid_without_committed_reservation',
         (SELECT count(*) FROM "order".orders o
           WHERE o.status = 'PAID'
             AND NOT EXISTS (SELECT 1 FROM catalog.stock_reservation r
                              WHERE r.order_id = o.id AND r.status = 'COMMITTED'))
  UNION ALL
  -- 취소된 주문의 재고 예약은 풀렸다(예약하지 못했으면 FAILED).
  SELECT 6, 'cancelled_with_held_reservation',
         (SELECT count(*) FROM "order".orders o
            JOIN catalog.stock_reservation r ON r.order_id = o.id
           WHERE o.status = 'CANCELLED' AND r.status NOT IN ('RELEASED', 'FAILED'))
  UNION ALL
  -- 잡혀 있는 재고. 모든 주문이 끝났으므로 0이다.
  SELECT 7, 'stock_still_reserved',
         (SELECT coalesce(sum(reserved), 0) FROM catalog.stock)
  UNION ALL
  -- 재고 차감이 확정된 예약과 맞는다. 시나리오가 모든 SKU를 같은 초기 재고(:initial_stock)로 만들므로 SKU마다
  -- 지금 재고 + 확정된 예약 수량 - 재고 조정 합계 = 초기 재고다. 맞지 않는 SKU 수를 센다.
  SELECT 8, 'stock_on_hand_mismatch',
         (SELECT count(*) FROM catalog.stock s
           WHERE s.on_hand
                 + (SELECT coalesce(sum(l.quantity), 0) FROM catalog.stock_reservation r
                      JOIN catalog.stock_reservation_line l ON l.reservation_id = r.id
                     WHERE r.status = 'COMMITTED' AND l.sku_id = s.sku_id)
                 - (SELECT coalesce(sum(a.delta), 0) FROM catalog.stock_adjustment a WHERE a.sku_id = s.sku_id)
                 <> :initial_stock)
) checks ORDER BY n;
