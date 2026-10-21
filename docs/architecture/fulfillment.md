# 배송·클레임 (fulfillment 서비스)

하위 주문의 배송, 구매확정, 클레임(취소·반품·교환)을 갖는다(FUL-1~3, CLM-1~3). 환불은 payment가 하고([payment.md](payment.md)), 주문 화면의 진행 상태는 order가 이 서비스의 이벤트로 갱신한다([order.md](order.md)). 흐름 전체는 [messaging.md](messaging.md) "클레임 환불 흐름"에 있다.

M4 시작(2026-10-21)에 정한 규칙이다. API와 화면은 각 PR에서 이 문서에 더한다.

## 범위 결정(2026-10-21)
- 주문 배송비는 M3처럼 무료다. 반품 배송비는 플랫폼 고정값 하나(`openmarket.fulfillment.return-shipping-fee`, 기본 6,000원, 왕복)다. 판매자별 배송비 정책은 만들지 않는다.
- 클레임은 하위 주문 전체를 대상으로 한다. 항목 일부만 취소·반품하지 않는다. 환불 단위가 하위 주문이라(PAY-2) 계산과 화면이 단순해진다.
- 교환은 같은 SKU로 다시 보내는 것만 한다. 판매자 귀책 사유만 교환할 수 있고, 단순 변심은 반품한 뒤 다시 주문한다. 추가 결제가 없고, 회수한 상품을 입고하고 새 상품을 보내 재고 변동이 없다.
- 택배사와 연동하지 않는다(FUL-2). 판매자가 송장을 입력하면 배송 시뮬레이터가 배송 중, 배송 완료로 바꾼다. 반품 회수도 따로 기록하지 않는다. 판매자가 회수품을 검수한 뒤 승인하거나 거부한다.
- 시간 기준값(시뮬레이터 지연, 자동 구매확정 기한, 작업 주기)은 설정이다. E2E는 환경 변수로 줄인다(결제 기한과 같은 방식).

## 배송(`Shipment`)
`order.order`의 `OrderPaid`를 받아 하위 주문마다 하나 만든다. 하위 주문 ID, 주문 ID, 판매자, 구매자, 금액, 결제 시각, 항목(주문 항목 ID, SKU, 상품, 상품명, 옵션, 카테고리, 개당 가격, 수량, 금액)을 이벤트에서 받아 고정한다. 교환 재발송은 같은 하위 주문에 새 배송을 만든다.
- 배송지는 저장하지 않는다. 판매자는 order의 하위 주문 상세에서 본다([order.md](order.md) "조회"). 구매자 개인정보를 한 곳에만 둔다.
- 첫 배송(교환 재발송이 아닌 것)은 하위 주문마다 하나다(부분 유일 인덱스 `shipment_first_idx`).

| 상태 | 뜻 | 바뀌는 때 |
|---|---|---|
| `READY` | 결제됐고 판매자 확인을 기다린다 | `OrderPaid`, 교환 승인 |
| `ACCEPTED` | 판매자가 발주를 확인했다 | 판매자 |
| `SHIPPED` | 송장을 입력하고 발송했다 | 판매자(택배사 코드, 송장 번호) |
| `IN_TRANSIT` | 배송 중이다 | 시뮬레이터: 발송 후 `in-transit-after`(기본 10분) |
| `DELIVERED` | 배송 완료 | 시뮬레이터: 배송 중이 된 뒤 `deliver-after`(기본 1시간) |
| `CANCELLED` | 발송 전 취소 클레임으로 멈췄다 | 취소 클레임 |

- 상태를 바꿀 때 배송 행을 잠근다. 판매자의 발송과 구매자의 취소가 겹치면 먼저 잠근 쪽이 이기고, 다른 쪽은 409다.
- 판매자 동작은 하위 주문의 마지막 배송에 한다. 교환 재발송이 있으면 새 배송이다.

### 발주 확인과 발송(FUL-1)
| 동작 | 할 수 있는 상태 | 같은 동작을 다시 하면 | 그 밖의 상태 |
|---|---|---|---|
| 발주 확인 | `READY` → `ACCEPTED` | `ACCEPTED`면 204, 이벤트 없음 | 409 `FULFILLMENT_INVALID_STATE` |
| 발송 | `ACCEPTED` → `SHIPPED`. 택배사와 송장 번호를 남긴다 | 이미 발송했고 택배사·송장 번호가 같으면 204, 이벤트 없음. 다르면 409 | 409 `FULFILLMENT_INVALID_STATE` |

- 발송은 `ACCEPTED`에서만 한다. 발주 확인을 건너뛰지 않는다.
- 409 응답에는 지금 배송 상태(`shipmentStatus`)가 실린다. 화면은 다시 읽어 그린다.
- 상태로 결과가 정해져 다시 보내도 같으므로 `Idempotency-Key`는 받지 않는다.
- 택배사(`Carrier`): `CJ_LOGISTICS`(CJ대한통운), `HANJIN`(한진택배), `LOTTE`(롯데택배), `LOGEN`(로젠택배), `KOREA_POST`(우체국택배). 택배사와 연동하지 않으므로 화면 이름과 코드만 둔다.
- 송장 번호: 숫자 10~14자리, 하이픈 없이. 어기면 400 `FULFILLMENT_INVALID_INPUT`이다. 실제 택배사 형식은 확인하지 않는다.

### 배송 시뮬레이터(FUL-2)
- `ShipmentSimulatorJob`은 `openmarket.fulfillment.simulator.interval`(기본 1분)마다 돌고 ShedLock(`fulfillment-simulator`)으로 한 인스턴스에서만 돈다.
- 발송 후 `in-transit-after`(기본 10분)가 지난 `SHIPPED`를 `IN_TRANSIT`으로, 배송 중이 된 뒤 `deliver-after`(기본 1시간)가 지난 `IN_TRANSIT`을 `DELIVERED`로 바꾼다. 한 주기에 한 단계만 바꾼다.
- 단계마다 기준 시각이 오래된 순으로 100건씩 읽고, 배송마다 트랜잭션을 따로 열어 잠그고 바꾼다. 그사이 상태가 바뀌었으면 넘긴다. 다 못 하면 다음 주기에 이어 한다.
- 바뀐 시각은 기준 시각(발송 시각 + 지연)이 아니라 작업이 바꾼 시각이다. 자동 구매확정 기한은 이 배송 완료 시각에서 센다.
- E2E는 환경 변수 `FULFILLMENT_SIMULATOR_INTERVAL`, `FULFILLMENT_IN_TRANSIT_AFTER`, `FULFILLMENT_DELIVER_AFTER`로 줄인다([dev-setup.md](../dev-setup.md) "E2E").

## 구매확정(`PurchaseConfirmation`, FUL-3)
하위 주문 하나에 한 번이다. 하위 주문의 마지막 배송이 `DELIVERED`이고 진행 중인 클레임(`REQUESTED`, `APPROVED`, `MEDIATING`)이 없을 때만 한다.
- 구매자 확정: 구매자가 직접 한다. 이미 확정했으면 그대로 204, 배송 완료 전이면 409 `FULFILLMENT_INVALID_STATE`(지금 배송 상태 `shipmentStatus`)다.
- 자동 확정(`PurchaseConfirmJob`): `openmarket.fulfillment.purchase-confirm.interval`(기본 1시간)마다 돌고 ShedLock(`fulfillment-purchase-confirm`)으로 한 인스턴스에서만 돈다. 확정 기한(`confirm_due_at`)이 지나고 확정되지 않은 배송 완료 배송을 기한이 오래된 순으로 100건씩, 배송마다 트랜잭션을 따로 열어 확정한다. 다 못 하면 다음 주기에 이어 한다.
- 구매자 확정과 자동 확정이 겹치면 하위 주문의 마지막 배송 행을 먼저 잠근 쪽이 확정하고, 다른 쪽은 이미 확정된 것을 보고 넘긴다.
- 확정 기한은 배송 완료 시각 + `openmarket.fulfillment.purchase-confirm.auto-confirm-after`(기본 7일)다. 시뮬레이터가 배송 완료로 바꿀 때 정한다. E2E는 `FULFILLMENT_CONFIRM_INTERVAL`, `FULFILLMENT_AUTO_CONFIRM_AFTER`로 줄인다. 반품·교환이 거부로 끝나면 거부 시각 + 7일과 비교해 늦은 쪽으로 미룬다. 구매자가 중재를 요청할 시간을 남기기 위해서다.
- 확정은 하위 주문 ID 유니크 제약으로 한 번만 생긴다. 확정한 뒤에는 클레임을 신청할 수 없다.
- 확정하면 `PurchaseConfirmed`(확정 방식 `BUYER`, `AUTO`)를 낸다. 리뷰 작성 자격과 정산 대상(M5)이 이것으로 생긴다.
- 진행 중인 클레임 확인은 클레임을 만드는 PR에서 더한다.

## 클레임(`Claim`, CLM-1~3)
하위 주문마다 진행 중인 클레임은 하나다. 거부로 끝난 클레임은 다음 신청을 막지 않는다.

| 유형 | 신청할 수 있을 때 | 승인 | 끝 |
|---|---|---|---|
| `CANCEL` | 배송이 `READY`, `ACCEPTED`(발송 전) | 신청과 동시에 승인한다(CLM-1). 배송은 `CANCELLED` | 환불 완료. 항목 전부 재입고 |
| `RETURN` | 마지막 배송이 `DELIVERED`, 구매확정 전 | 판매자가 회수품을 검수하고 승인한다. 재입고 여부를 고른다 | 환불 완료 |
| `EXCHANGE` | `RETURN`과 같다. 판매자 귀책 사유만 | 판매자가 승인하면 새 배송(`READY`)을 만든다 | 새 배송 발송. 환불·재입고 없음 |

| 상태 | 뜻 | 다음 상태 |
|---|---|---|
| `REQUESTED` | 판매자 판단을 기다린다 | `APPROVED`, `REJECTED` |
| `APPROVED` | 승인됐다. 환불 또는 재발송을 기다린다 | `COMPLETED` |
| `REJECTED` | 판매자가 사유를 남기고 거부했다(CLM-3). 중재를 거쳐 거부가 확정돼도 이 상태다 | `MEDIATING`(한 번만) |
| `MEDIATING` | 구매자가 중재를 요청했고 관리자 결정을 기다린다 | `APPROVED`, `REJECTED` |
| `COMPLETED` | 환불 또는 재발송이 끝났다 | |

- 반품·교환 신청에는 사유 코드, 설명(1~500자), 사진(`CLAIM_IMAGE`, 0~5장)을 넣는다(CLM-2).
- 중재 요청은 거부 후 7일 안에, 구매확정 전에 한 번만 할 수 있다. 관리자는 승인(귀책을 고른다, 재입고 안 함)하거나 거부를 확정한다.
- `RefundFailed`면 클레임은 `APPROVED`로 남는다. 관리자가 payment에서 환불을 다시 시도한다([messaging.md](messaging.md) "클레임 환불 흐름").

### 사유와 귀책(`Fault`)
| 사유 | 귀책 | 교환 |
|---|---|---|
| `CHANGE_OF_MIND` 단순 변심 | `BUYER` | 안 됨 |
| `DEFECTIVE` 상품 불량 | `SELLER` | 됨 |
| `WRONG_ITEM` 오배송 | `SELLER` | 됨 |
| `NOT_AS_DESCRIBED` 상품 정보와 다름 | `SELLER` | 됨 |
| `BUYER_CANCEL` 발송 전 취소(`CANCEL` 전용) | `BUYER` | |

- 귀책은 사유로 정한다. 판매자가 귀책에 동의하지 않으면 거부하고, 구매자가 중재를 요청하면 관리자가 귀책을 고른다.

### 환불 금액
하위 주문 금액 − 반품 배송비. 반품 배송비는 `RETURN`이고 귀책이 `BUYER`일 때만 뺀다. 결과가 0보다 작으면 0이다.
- `CANCEL`은 발송 전이라 배송비가 없다. 하위 주문 금액 전액이다.
- 쿠폰 할인(M5)은 주문 항목 금액 비율로 하위 주문에 나눠 뺀다. 계산식은 M5에서 이 절에 더한다.
- 환불 금액이 0이면 payment는 토스를 부르지 않고 바로 `RefundCompleted`를 낸다.

## 이벤트
필드는 [context-map.md](context-map.md) "fulfillment.shipment", "fulfillment.claim". 두 토픽 모두 키는 하위 주문 ID다. 한 하위 주문의 배송 이벤트끼리, 클레임 이벤트끼리는 순서대로 오지만 두 토픽 사이 순서는 보장하지 않는다.

| 받는 이벤트 | 처리 |
|---|---|
| `OrderPaid`(order.order) | 하위 주문마다 배송 `READY`. 하위 주문의 첫 배송이 이미 있으면 넘긴다. 이벤트는 내지 않는다 |
| `RefundCompleted`(payment.payment) | 클레임 `APPROVED` → `COMPLETED` + `ClaimCompleted` |
| `RefundFailed`(payment.payment) | 클레임은 그대로 둔다. 실패 코드를 기록한다 |

| 내는 이벤트 | 언제 |
|---|---|
| `ShipmentAccepted` | 발주 확인 |
| `ShipmentShipped` | 발송 |
| `ShipmentInTransit` | 시뮬레이터가 배송 중으로 바꿨다 |
| `ShipmentDelivered` | 시뮬레이터가 배송 완료로 바꿨다 |
| `PurchaseConfirmed` | 구매자 확정, 자동 확정 |

- 리스너는 처리 기록(`processed_event`)으로 같은 이벤트를 한 번만 처리한다. 리스너 이름은 `fulfillment.<이벤트>`다(예: `fulfillment.order-paid`).
- 항목에 주문 항목 ID·상품명·옵션이 없는 M4 이전 형식의 `OrderPaid`는 배송을 만들 수 없어 영구 오류(`InvalidEventException`)로 바로 DLT에 보낸다. fulfillment 소비자 그룹은 `order.order`를 처음부터 읽으므로, M4 전에 결제된 개발용 스택의 주문이 여기에 걸린다(2026-10-21 확인). 빈 볼륨으로 띄운 스택에는 없다.

## API
gateway 경로는 `/api/fulfillment` + 아래 경로다. 판매자 경로는 `SELLER` 역할이 있어야 하고 주체는 토큰의 `sub`(= sellerId)다. 구매자 경로의 주체는 토큰의 `sub`(= buyerId)다.
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/seller-orders/{sellerOrderId}` | 구매자 본인 하위 주문의 배송 조회(FUL-2). 판매자 조회와 같은 진행 상태·배송 목록에 구매확정 시각·방식, 확정 기한을 더한다. 남의 것이거나 배송이 아직 없으면 404 `FULFILLMENT_NOT_FOUND` |
| POST | `/v1/seller-orders/{sellerOrderId}/confirm` | 구매확정(FUL-3). 204. 위 "구매확정" |
| GET | `/v1/seller/seller-orders/{sellerOrderId}` | 본인 하위 주문의 배송 진행. 진행 상태(마지막 배송의 상태)와 배송 목록(배송 ID, 상태, 택배사, 송장 번호, 만든·발주 확인·발송·배송 중·배송 완료 시각. 만든 순서)이다. 다른 판매자의 것이거나 배송이 아직 없으면 404 `FULFILLMENT_NOT_FOUND` |
| POST | `/v1/seller/seller-orders/{sellerOrderId}/accept` | 발주 확인. 204. 위 "발주 확인과 발송" |
| POST | `/v1/seller/seller-orders/{sellerOrderId}/ship` | 발송. 본문 `{"carrier", "trackingNumber"}`. 204 |

- 발주 확인과 발송도 다른 판매자의 하위 주문이나 배송이 없는 하위 주문이면 404 `FULFILLMENT_NOT_FOUND`다.
- 하위 주문 목록, 항목, 배송지는 order의 판매자 하위 주문 조회에 있다([order.md](order.md) "조회"). 이 API는 처리 화면이 배송 상태와 할 수 있는 동작을 정하는 데 쓴다.
- 배송은 `OrderPaid`를 받아 만들므로 결제 직후 잠깐 없다. 화면은 404인 동안 "배송 준비 중"으로 보여 준다.
