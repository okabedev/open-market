# 배송·클레임 (fulfillment 서비스)

하위 주문의 배송, 구매확정, 클레임(취소·반품·교환)을 갖는다(FUL-1~3, CLM-1~3). 환불은 payment가 하고([payment.md](payment.md)), 주문 화면의 진행 상태는 order가 이 서비스의 이벤트로 갱신한다([order.md](order.md)). 흐름 전체는 [messaging.md](messaging.md) "클레임 환불 흐름"에 있다.

M4 시작(2026-10-21)에 정한 규칙에 M4 동안 만든 API와 화면을 더했다.

## 범위 결정(2026-10-21)
- 주문 배송비는 M3처럼 무료다. 반품 배송비는 플랫폼 고정값 하나(`openmarket.fulfillment.return-shipping-fee`, 기본 6,000원, 왕복)다. 판매자별 배송비 정책은 만들지 않는다.
- 클레임은 하위 주문 전체를 대상으로 한다. 항목 일부만 취소·반품하지 않는다. 환불 단위가 하위 주문이라(PAY-2) 계산과 화면이 단순해진다.
- 교환은 같은 SKU로 다시 보내는 것만 한다. 판매자 귀책 사유만 교환할 수 있고, 단순 변심은 반품한 뒤 다시 주문한다. 추가 결제가 없고, 회수한 상품을 입고하고 새 상품을 보내 재고 변동이 없다.
- 택배사와 연동하지 않는다(FUL-2). 판매자가 송장을 입력하면 배송 시뮬레이터가 배송 중, 배송 완료로 바꾼다. 반품 회수도 따로 기록하지 않는다. 판매자가 회수품을 검수한 뒤 승인하거나 거부한다.
- 시간 기준값(시뮬레이터 지연, 자동 구매확정 기한, 작업 주기)은 설정이다. E2E는 환경 변수로 줄인다(결제 기한과 같은 방식).

## 배송(`Shipment`)
`order.order`의 `OrderPaid`를 받아 하위 주문마다 하나 만든다. 하위 주문 ID, 주문 ID, 판매자, 구매자, 상품 금액, 판매자 쿠폰 할인, 하위 주문 결제 금액, 결제 시각, 항목(주문 항목 ID, SKU, 상품, 상품명, 옵션, 카테고리, 개당 가격, 수량, 금액)을 이벤트에서 받아 고정한다. 교환 재발송은 같은 하위 주문에 새 배송을 만든다.
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
- 구매자 확정: 구매자가 직접 한다. 이미 확정했으면 그대로 204, 배송 완료 전이면 409 `FULFILLMENT_INVALID_STATE`(지금 배송 상태 `shipmentStatus`), 진행 중인 클레임이 있으면 409 `FULFILLMENT_CLAIM_IN_PROGRESS`다.
- 자동 확정(`PurchaseConfirmJob`): `openmarket.fulfillment.purchase-confirm.interval`(기본 1시간)마다 돌고 ShedLock(`fulfillment-purchase-confirm`)으로 한 인스턴스에서만 돈다. 확정 기한(`confirm_due_at`)이 지나고 확정되지 않았고 진행 중인 클레임이 없는 배송 완료 배송을 기한이 오래된 순으로 100건씩, 배송마다 트랜잭션을 따로 열어 확정한다. 다 못 하면 다음 주기에 이어 한다.
- 구매자 확정과 자동 확정이 겹치면 하위 주문의 마지막 배송 행을 먼저 잠근 쪽이 확정하고, 다른 쪽은 이미 확정된 것을 보고 넘긴다.
- 확정 기한은 배송 완료 시각 + `openmarket.fulfillment.purchase-confirm.auto-confirm-after`(기본 7일)다. 시뮬레이터가 배송 완료로 바꿀 때 정한다. E2E는 `FULFILLMENT_CONFIRM_INTERVAL`, `FULFILLMENT_AUTO_CONFIRM_AFTER`로 줄인다. 반품·교환이 거부되면 거부 시각 + 중재 요청 기한(`openmarket.fulfillment.claim.mediation-window`, 기본 7일)과 비교해 늦은 쪽으로 미룬다. 구매자가 중재를 요청할 시간을 남기기 위해서다.
- 자동 확정은 하위 주문의 마지막 배송만 본다. 교환 재발송이 있으면 처음 배송의 기한은 쓰지 않는다.
- 확정은 하위 주문 ID 유니크 제약으로 한 번만 생긴다. 확정한 뒤에는 클레임을 신청할 수 없다.
- 확정하면 `PurchaseConfirmed`(확정 방식 `BUYER`, `AUTO`)를 낸다. 리뷰 작성 자격([review.md](review.md)), 정산 대상([settlement.md](settlement.md)), 검색 판매량([search.md](search.md))이 이것으로 생긴다.
- 클레임 신청과 확정도 하위 주문의 마지막 배송 행을 잠그고 하므로, 겹치면 먼저 잠근 쪽만 된다.

## 클레임(`Claim`, CLM-1~3)
하위 주문마다 진행 중인 클레임(`REQUESTED`, `APPROVED`, `MEDIATING`)은 하나다(부분 유일 인덱스 `claim_ongoing_idx`). 거부로 끝난 클레임은 다음 신청을 막지 않는다. 클레임은 대상 배송(신청 때 하위 주문의 마지막 배송)을 가리킨다.

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
- 클레임을 바꾸는 동작(승인, 거부, 환불 결과)은 클레임 행을 잠그고 한다. 신청과 구매확정은 하위 주문의 마지막 배송 행을 잠근다.

### 발송 전 취소(CLM-1)
구매자가 `POST /v1/seller-orders/{sellerOrderId}/cancel`로 한다. 본문은 없다(사유는 `BUYER_CANCEL`).
- 하위 주문의 마지막 배송 행을 잠그고, 배송이 `READY`, `ACCEPTED`면 배송을 `CANCELLED`로 바꾸고 클레임을 `APPROVED`로 만든다. 환불 금액은 하위 주문 결제 금액 전액이다(아래 "환불 금액"). `ClaimRequested`와 `ClaimApproved`를 같은 트랜잭션에서 낸다.
- 이미 취소했으면(배송이 `CANCELLED`) 그대로 204, 이벤트 없음이다. 다시 보내도 결과가 같아 `Idempotency-Key`는 받지 않는다.
- 발송한 뒤면 409 `FULFILLMENT_INVALID_STATE`(지금 배송 상태 `shipmentStatus`)다. 판매자의 발송과 겹치면 배송 행을 먼저 잠근 쪽이 이긴다.
- 진행 중인 클레임이 있으면 409 `FULFILLMENT_CLAIM_IN_PROGRESS`다. 교환 승인으로 만든 새 배송(`READY`)이 여기에 걸린다. 교환을 취소하는 기능은 없다.
- 남의 하위 주문이거나 배송이 아직 없으면 404 `FULFILLMENT_NOT_FOUND`다. 결제 직후 배송이 생기기 전에는 취소할 수 없다.

### 반품·교환 신청(CLM-2)
구매자가 `POST /v1/seller-orders/{sellerOrderId}/claims`로 한다. `Idempotency-Key`가 필요하다. 응답은 201 `{claimId}`다.
| 항목 | 규칙 |
|---|---|
| `type` | `RETURN` 또는 `EXCHANGE`. 발송 전 취소는 위 "발송 전 취소"로 한다 |
| `reason` | `CHANGE_OF_MIND`, `DEFECTIVE`, `WRONG_ITEM`, `NOT_AS_DESCRIBED`. 교환은 판매자 귀책 사유만 |
| `description` | 1~500자. 앞뒤 공백은 지운다 |
| `fileIds` | 클레임 사진(`CLAIM_IMAGE`) 0~5장, 중복 없이. file이 `ClaimRequested`를 받아 붙인다([file.md](file.md) "클레임 사진") |

- 규칙을 어기면 400 `FULFILLMENT_INVALID_INPUT`이다.
- 하위 주문의 마지막 배송 행을 잠그고, 배송이 `DELIVERED`가 아니면 409 `FULFILLMENT_INVALID_STATE`(`shipmentStatus`), 구매확정했으면 409 `FULFILLMENT_PURCHASE_CONFIRMED`, 진행 중인 클레임이 있으면 409 `FULFILLMENT_CLAIM_IN_PROGRESS`다. 남의 하위 주문은 404 `FULFILLMENT_NOT_FOUND`다.
- 클레임은 `REQUESTED`로 만들고 `ClaimRequested`를 낸다. 귀책은 사유로 정한다.

### 판매자 승인과 거부(CLM-3)
판매자는 회수품을 검수한 뒤 승인하거나 거부한다. 회수 자체는 기록하지 않는다(위 "범위 결정").
| 동작 | 경로 | 할 수 있는 상태 | 결과 |
|---|---|---|---|
| 승인 | `POST /v1/seller/claims/{claimId}/approve` 본문 `{"restock"}` | `REQUESTED` | `APPROVED` + `ClaimApproved`. 반품은 재입고 여부(`restock`, 반품이면 필수)를 남긴다. 교환은 새 배송을 만든다(아래 "교환 재발송") |
| 거부 | `POST /v1/seller/claims/{claimId}/reject` 본문 `{"reason"}` | `REQUESTED` | `REJECTED` + `ClaimRejected`(`mediated=false`). 거부 사유는 1~500자 |

- 같은 동작을 다시 하면(이미 승인했는데 승인, 이미 거부했는데 거부) 204, 이벤트 없음이다. 상태로 결과가 정해져 `Idempotency-Key`는 받지 않는다. 그 밖의 상태면 409 `FULFILLMENT_CLAIM_INVALID_STATE`(지금 클레임 상태 `claimStatus`)다.
- 다른 판매자의 클레임이거나 발송 전 취소 클레임이면 404 `FULFILLMENT_CLAIM_NOT_FOUND`다.
- 반품 승인의 환불 금액은 아래 "환불 금액"이다. 반품 배송비는 `openmarket.fulfillment.return-shipping-fee`(기본 6,000원)다.
- 거부하면 하위 주문의 마지막 배송의 자동 구매확정 기한을 거부 시각 + 중재 요청 기한과 비교해 늦은 쪽으로 미룬다(위 "구매확정").

### 중재(CLM-3, ADM-2)
판매자가 거부한 반품·교환을 구매자가 관리자에게 다시 판단해 달라고 요청한다.
- 구매자 요청 `POST /v1/claims/{claimId}/mediation`(본문 없음, 204). 하위 주문의 마지막 배송 행을 잠그고 클레임을 잠가 `REJECTED` → `MEDIATING`으로 바꾸고 `ClaimMediationRequested`를 낸다.
  - 거부 시각 + 중재 요청 기한(`openmarket.fulfillment.claim.mediation-window`, 기본 7일) 안에만 된다. 지나면 409 `FULFILLMENT_MEDIATION_CLOSED`다.
  - 클레임마다 한 번이다. 이미 `MEDIATING`이면 204, 중재를 거쳐 거부가 확정됐거나 그 밖의 상태면 409 `FULFILLMENT_CLAIM_INVALID_STATE`(`claimStatus`)다.
  - 구매확정했으면 409 `FULFILLMENT_PURCHASE_CONFIRMED`, 그사이 새 클레임을 신청해 진행 중이면 409 `FULFILLMENT_CLAIM_IN_PROGRESS`다. 남의 클레임이거나 발송 전 취소면 404 `FULFILLMENT_CLAIM_NOT_FOUND`다.
- 관리자 목록 `GET /v1/admin/claims?page=&size=`: 중재 중인 클레임을 중재 요청이 오래된 순으로 준다(쪽 번호, `size` 1~100, 기본 20). 줄은 클레임 조회의 필드에 판매자·구매자 ID, 하위 주문 금액, 중재 요청 시각을 더한 것이다. 사진은 file의 관리자 경로로 받는다([file.md](file.md) "비공개 파일").
- 관리자 결정(`MEDIATING`에서만. 같은 결정을 다시 하면 204, 다른 상태면 409 `FULFILLMENT_CLAIM_INVALID_STATE`)
  | 결정 | 경로 | 결과 |
  |---|---|---|
  | 승인 | `POST /v1/admin/claims/{claimId}/approve` 본문 `{"fault"}` | 관리자가 귀책(`BUYER`, `SELLER`)을 고른다. `APPROVED` + `ClaimApproved`. 반품은 재입고하지 않고, 환불 금액은 고른 귀책으로 정한다. 교환은 귀책이 `SELLER`여야 하고(어기면 400 `FULFILLMENT_INVALID_INPUT`) 새 배송을 만든다 |
  | 거부 확정 | `POST /v1/admin/claims/{claimId}/reject` 본문 `{"reason"}` | `REJECTED` + `ClaimRejected`(`mediated=true`). 사유 1~500자. 판매자의 거부 사유는 따로 남는다. 다시 중재를 요청할 수 없다 |
- 관리자 결정은 감사 로그(`CLAIM_MEDIATION_APPROVED`, `CLAIM_MEDIATION_REJECTED`, 관리자 ID, 클레임 ID, 귀책 또는 사유)를 남긴다(NFR-8).
- 중재 중에는 진행 중인 클레임이라 자동 구매확정이 되지 않는다. 거부가 확정되면 그때 지난 기한으로 자동 확정될 수 있다.

### 교환 재발송
- 교환을 승인하면 대상 배송과 같은 항목·금액으로 새 배송(`READY`, `claim_id` = 교환 클레임)을 만든다. 판매자는 하위 주문의 마지막 배송, 곧 새 배송을 발주 확인하고 발송한다. 배송 이벤트는 처음 배송과 같이 나간다.
- 새 배송을 발송하면 같은 트랜잭션에서 교환 클레임을 `COMPLETED`로 바꾸고 `ClaimCompleted`(환불 0, 재입고 없음)를 낸다. 회수한 상품을 입고하고 새 상품을 보내 재고 변동이 없다.
- 새 배송이 배송 완료되면 그 시각부터 자동 구매확정 기한을 다시 센다. 교환이 끝난 뒤 다시 반품·교환을 신청할 수 있다.

### 환불 결과 반영
- `RefundCompleted`: 클레임을 잠그고 `APPROVED`면 `COMPLETED`로 바꾸고 `ClaimCompleted`를 낸다. 재입고 항목(`restockLines`)은 `CANCEL`이거나 재입고를 고른 `RETURN`이면 대상 배송의 항목 전부를 SKU별로 합친 것이고, 그 밖에는 빈 목록이다. 이미 `COMPLETED`면 넘긴다.
- `RefundFailed`: 클레임은 `APPROVED`로 두고 실패 코드(`refund_failure_code`)를 남긴다. 관리자 재시도로 `RefundCompleted`가 오면 지운다.
- 모르는 클레임의 환불 결과는 경고 로그만 남기고 넘긴다.

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
하위 주문 결제 금액 − 반품 배송비. 반품 배송비는 `RETURN`이고 귀책이 `BUYER`일 때만 뺀다. 결과가 0보다 작으면 0이다.
- 하위 주문 결제 금액은 `OrderPaid`의 하위 주문 paymentAmount(상품 금액 − 판매자 쿠폰 할인 − 플랫폼 쿠폰 할인 몫)다. 배송을 만들 때 판매자 쿠폰 할인과 함께 받아 둔다(`payment_amount`, `seller_coupon_discount`). 나누는 방법은 [promotion.md](promotion.md) "할인 계산". 쿠폰은 돌려주지 않는다(2026-10-22 결정). M5 이전 배송과, 쿠폰 필드가 없는 M5 이전 형식의 `OrderPaid`로 만든 배송은 상품 금액과 같고 판매자 쿠폰 할인은 0이다.
- `CANCEL`은 발송 전이라 배송비가 없다. 하위 주문 결제 금액 전액이다.
- 환불 금액이 0이면 payment는 토스를 부르지 않고 바로 `RefundCompleted`를 낸다.

## 이벤트
필드는 [context-map.md](context-map.md) "fulfillment.shipment", "fulfillment.claim". 두 토픽 모두 키는 하위 주문 ID다. 한 하위 주문의 배송 이벤트끼리, 클레임 이벤트끼리는 순서대로 오지만 두 토픽 사이 순서는 보장하지 않는다.

| 받는 이벤트 | 처리 |
|---|---|
| `OrderPaid`(order.order) | 하위 주문마다 배송 `READY`. 하위 주문의 첫 배송이 이미 있으면 넘긴다. 이벤트는 내지 않는다 |
| `RefundCompleted`(payment.payment) | 클레임 `APPROVED` → `COMPLETED` + `ClaimCompleted`(위 "환불 결과 반영") |
| `RefundFailed`(payment.payment) | 클레임은 그대로 둔다. 실패 코드를 기록한다 |

| 내는 이벤트 | 언제 |
|---|---|
| `ShipmentAccepted` | 발주 확인 |
| `ShipmentShipped` | 발송 |
| `ShipmentInTransit` | 시뮬레이터가 배송 중으로 바꿨다 |
| `ShipmentDelivered` | 시뮬레이터가 배송 완료로 바꿨다 |
| `PurchaseConfirmed` | 구매자 확정, 자동 확정 |
| `ClaimRequested` | 발송 전 취소, 반품·교환 신청 |
| `ClaimApproved` | 발송 전 취소(신청과 함께), 판매자 승인, 중재 승인 |
| `ClaimRejected` | 판매자 거부, 중재 거부 확정 |
| `ClaimMediationRequested` | 구매자 중재 요청 |
| `ClaimCompleted` | 환불 완료, 교환 재발송 |

- 리스너는 처리 기록(`processed_event`)으로 같은 이벤트를 한 번만 처리한다. 리스너 이름은 `fulfillment.<이벤트>`다(예: `fulfillment.order-paid`).
- 항목에 주문 항목 ID·상품명·옵션이 없는 M4 이전 형식의 `OrderPaid`는 배송을 만들 수 없어 영구 오류(`InvalidEventException`)로 바로 DLT에 보낸다. fulfillment 소비자 그룹은 `order.order`를 처음부터 읽으므로, M4 전에 결제된 개발용 스택의 주문이 여기에 걸린다(2026-10-21 확인). 빈 볼륨으로 띄운 스택에는 없다.

## API
gateway 경로는 `/api/fulfillment` + 아래 경로다. 판매자 경로는 `SELLER` 역할이 있어야 하고 주체는 토큰의 `sub`(= sellerId)다. 구매자 경로의 주체는 토큰의 `sub`(= buyerId)다.
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/seller-orders/{sellerOrderId}` | 구매자 본인 하위 주문의 배송 조회(FUL-2). 판매자 조회와 같은 진행 상태·배송 목록에 구매확정 시각·방식, 확정 기한을 더한다. 남의 것이거나 배송이 아직 없으면 404 `FULFILLMENT_NOT_FOUND` |
| POST | `/v1/seller-orders/{sellerOrderId}/confirm` | 구매확정(FUL-3). 204. 위 "구매확정" |
| POST | `/v1/seller-orders/{sellerOrderId}/cancel` | 발송 전 취소(CLM-1). 204. 위 "발송 전 취소" |
| POST | `/v1/seller-orders/{sellerOrderId}/claims` | 반품·교환 신청(CLM-2). 본문 `{"type", "reason", "description", "fileIds"}`. 201 `{claimId}`. 위 "반품·교환 신청" |
| POST | `/v1/claims/{claimId}/mediation` | 중재 요청. 204. 위 "중재" |
| GET | `/v1/seller/seller-orders/{sellerOrderId}` | 본인 하위 주문의 배송 진행. 진행 상태(마지막 배송의 상태)와 배송 목록(배송 ID, 상태, 택배사, 송장 번호, 만든·발주 확인·발송·배송 중·배송 완료 시각. 만든 순서)이다. 다른 판매자의 것이거나 배송이 아직 없으면 404 `FULFILLMENT_NOT_FOUND` |
| POST | `/v1/seller/seller-orders/{sellerOrderId}/accept` | 발주 확인. 204. 위 "발주 확인과 발송" |
| POST | `/v1/seller/seller-orders/{sellerOrderId}/ship` | 발송. 본문 `{"carrier", "trackingNumber"}`. 204 |
| POST | `/v1/seller/claims/{claimId}/approve` | 반품·교환 승인. 본문 `{"restock"}`. 204. 위 "판매자 승인과 거부" |
| POST | `/v1/seller/claims/{claimId}/reject` | 반품·교환 거부. 본문 `{"reason"}`. 204 |
| GET | `/v1/admin/claims?page=&size=` | 중재 중인 클레임 목록. `ADMIN` |
| POST | `/v1/admin/claims/{claimId}/approve` | 중재 승인. 본문 `{"fault"}`. 204 |
| POST | `/v1/admin/claims/{claimId}/reject` | 중재 거부 확정. 본문 `{"reason"}`. 204 |

- 구매자·판매자 조회 모두 클레임 목록(클레임 ID, 유형, 상태, 사유, 귀책, 설명, 사진 ID, 재입고 여부, 환불 금액, 환불 실패 코드, 거부 사유, 중재 거부 사유, 신청·승인·거부·중재 요청·완료 시각. 신청 순서)을 함께 준다. 사진은 file의 비공개 경로로 받는다([file.md](file.md) "비공개 파일").
- 발주 확인과 발송도 다른 판매자의 하위 주문이나 배송이 없는 하위 주문이면 404 `FULFILLMENT_NOT_FOUND`다.
- 하위 주문 목록, 항목, 배송지는 order의 판매자 하위 주문 조회에 있다([order.md](order.md) "조회"). 이 API는 처리 화면이 배송 상태와 할 수 있는 동작을 정하는 데 쓴다.

## 화면
- seller 하위 주문 상세(`/orders/{sellerOrderId}`)가 처리 화면이다. 항목·배송지는 order, 배송·클레임은 이 서비스에서 읽는다.
  - 마지막 배송이 `READY`면 발주 확인, `ACCEPTED`면 송장 입력 폼을 보인다. 교환 재발송이면 제목에 적는다.
  - 판매자를 기다리는 반품·교환(`REQUESTED`)은 승인(반품은 재입고 여부를 꼭 고른다)과 거부(사유) 폼을 보인다. 사진은 BFF로 받는다([file.md](file.md) "비공개 파일").
  - 409면 화면을 다시 읽고 "그사이 주문 상태가 바뀌었다"고 알린다.
  - 송장 번호의 공백·하이픈은 BFF가 빼고 보낸다.
- seller 하위 주문 목록은 할 일 탭으로 거른다([order.md](order.md) "조회").
- shop 주문 상세는 결제된 주문의 하위 주문마다 진행 상태와 배송 조회(`/orders/{orderId}/seller-orders/{sellerOrderId}`) 링크를 보인다. 배송 조회가 구매자 처리 화면이다.
  - 배송 이력(송장), 확정 기한 또는 확정 시각, 상품, 클레임(사진 포함)을 보인다.
  - 버튼은 마지막 배송과 진행 중인 클레임으로 정한다. 발송 전(`READY`, `ACCEPTED`, 첫 배송)이면 취소, 배송 완료이고 확정 전이며 진행 중인 클레임이 없으면 구매확정과 반품·교환 신청이다. 판매자가 거부한 클레임에 중재를 요청한 적이 없으면 중재 요청 버튼을 보인다. 기한(7일)과 그 밖의 거절은 fulfillment가 409로 알리고 화면이 문구를 보인다.
  - 반품·교환 신청(`.../claim`)은 유형, 사유(교환이면 판매자 귀책 사유만), 설명, 사진(고르는 즉시 BFF `/api/files`로 올린다)을 받는다. 화면을 그릴 때 만든 멱등성 키를 보낸다.
- admin 클레임 중재(`/claims`)는 중재 중인 클레임을 요청이 오래된 순으로 보이고, 클레임마다 승인(귀책을 고른다. 교환은 판매자만)과 거부 확정(사유)을 한다. 사진은 BFF `/api/admin/claim-images/...`가 file의 관리자 경로로 받는다. 결정하면 목록에서 빠지고 `?notice=`로 결과를 보인다.
- 배송은 `OrderPaid`를 받아 만들므로 결제 직후 잠깐 없다. 화면은 404인 동안 "배송 준비 중"으로 보여 준다.
