# 주문 (order 서비스)

장바구니, 주문과 하위 주문, 결제 결과 반영, 주문 만료를 갖는다(ORD-1~4). 주문 흐름 전체(Saga)는 [messaging.md](messaging.md)에 있다.

## 장바구니(ORD-1)
- 회원당 하나다(`Cart`). 처음 담을 때 만든다. 담긴 줄(`CartItem`)은 SKU와 수량만 갖는다. 상품명, 가격, 판매자, 판매 가능 수량은 저장하지 않고 볼 때마다 catalog에서 읽는다(아래 "catalog 조회"). 장바구니는 주문이 아니라서 가격을 고정하지 않는다. 가격 스냅샷은 주문할 때 만든다.
- 주체는 토큰의 `sub`(= memberId)다.

### 규칙
| 항목 | 규칙 | 어기면 |
|---|---|---|
| 수량 | 1~99 | 400 `ORDER_INVALID_INPUT` |
| 담은 SKU 수 | 50개까지 | 422 `ORDER_CART_FULL` |
| 같은 SKU를 다시 담기 | 수량을 더한다. 더한 수량도 99까지 | 422 `ORDER_CART_QUANTITY_EXCEEDED` |
| 담을 수 있는 SKU | catalog에 있고 상품이 판매 중(`ON_SALE`) | 없으면 404 `ORDER_SKU_NOT_FOUND`, 판매 중이 아니면 422 `ORDER_SKU_NOT_ON_SALE` |

- 담을 때 재고는 보지 않는다. 재고는 계속 바뀌고, 주문할 때 예약으로 확정한다. 장바구니 조회가 줄마다 상태를 보여 준다.
- 수량 바꾸기와 빼기는 catalog를 부르지 않는다. 판매가 멈춘 상품도 바꾸거나 뺄 수 있어야 하기 때문이다.
- 같은 회원의 요청이 동시에 와도 줄 수와 수량이 어긋나지 않게 장바구니 행을 잠그고(`SELECT ... FOR UPDATE`) 바꾼다. 장바구니가 없으면 먼저 만든다(`INSERT ... ON CONFLICT DO NOTHING`).

### 조회
- 판매자별로 묶는다. 묶음은 가장 최근에 담은 줄이 있는 판매자부터, 묶음 안의 줄은 최근에 담은 순이다.
- 줄 상태(`CartItemStatus`)
  | 상태 | 조건 |
  |---|---|
  | `AVAILABLE` | 판매 중이고 판매 가능 수량이 담은 수량 이상 |
  | `INSUFFICIENT_STOCK` | 판매 중이고 판매 가능 수량이 1 이상이지만 담은 수량보다 적다 |
  | `SOLD_OUT` | 판매 중이고 판매 가능 수량이 0 |
  | `NOT_ON_SALE` | 판매 중지, 제재, 등록 중 |
- 판매 가능 수량은 그대로 보여 주지 않는다. 상태로만 알린다(catalog 구매자 상세와 같다).
- catalog에 없는 SKU(정상이라면 생기지 않는다. catalog는 SKU를 지우지 않는다)는 묶음에 넣지 않고 `missingSkuIds`로 따로 준다. 화면은 빼기를 안내한다.

### API
gateway 경로는 `/api/order` + 아래 경로다.
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/cart` | 장바구니. 비어 있어도 200 |
| POST | `/v1/cart/items` | 담기. 본문 `{"skuId", "quantity"}`. 수량을 더하므로 `Idempotency-Key` 필수. 204 |
| PUT | `/v1/cart/items/{skuId}` | 수량 바꾸기. 본문 `{"quantity"}`. 담지 않은 SKU면 404 `ORDER_CART_ITEM_NOT_FOUND`. 204 |
| DELETE | `/v1/cart/items/{skuId}` | 빼기. 담지 않은 SKU여도 204 |

## 주문(ORD-2)
구매자가 장바구니의 일부나 상품 하나를 주문한다. 요청 형식은 같다. 주문 하나는 판매자별 하위 주문(`SellerOrder`)으로 나뉘고, 하위 주문은 주문 항목(`OrderLine`)을 갖는다.

### 주문 생성
`POST /v1/orders`(`Idempotency-Key` 필수). 본문은 줄 목록(`skuId`, `quantity`, `expectedPrice`)과 배송지다.
1. 입력을 확인한다. 어기면 400 `ORDER_INVALID_INPUT`이다.
   | 항목 | 규칙 |
   |---|---|
   | 줄 | 1~50개. 같은 SKU는 한 줄만 |
   | 수량 | 1~99 |
   | 기대 가격 | 주문서 화면이 보여 준 개당 가격(원). 0 이상 |
   | 받는 사람 | 1~50자 |
   | 연락처 | 하이픈으로 나눈 국내 전화번호(`010-1234-5678`) |
   | 우편번호 | 숫자 5자리 |
   | 기본 주소 | 1~200자 |
   | 상세 주소 | 선택, 200자까지 |
   - 배송지 규칙은 member 배송지와 같다([member.md](member.md) "규칙"). 배송지는 BFF가 member 배송지 목록에서 고른 내용을 그대로 보낸다. order는 member를 부르지 않는다(2026-10-20 결정).
2. catalog에서 SKU를 읽고(아래 "catalog 조회") 다시 확인한다. 하나라도 걸리면 주문을 만들지 않는다.
   | 확인 | 어기면 |
   |---|---|
   | catalog에 있다 | 404 `ORDER_SKU_NOT_FOUND`, `skuIds` |
   | 상품이 판매 중(`ON_SALE`)이다 | 422 `ORDER_SKU_NOT_ON_SALE`, `skuIds` |
   | 지금 가격이 기대 가격과 같다(ORD-2) | 409 `ORDER_PRICE_CHANGED`, `changes`(skuId, expectedPrice, currentPrice) |
   | 판매 가능 수량이 주문 수량 이상이다 | 422 `ORDER_INSUFFICIENT_STOCK`, `skuIds` |
   - 확장 필드(`skuIds`, `changes`)는 오류 응답(ProblemDetail)에 함께 실린다. 화면은 이것으로 어느 상품이 문제인지 보여 준다.
   - 수량 확인은 빨리 알리기 위한 것이다. 재고는 catalog 예약이 확정한다. 여기를 지나도 예약에서 모자라면 주문이 취소된다.
3. 주문을 `PENDING`으로 저장하고 같은 트랜잭션에서 `OrderPlaced`를 낸다. 201 `{orderId}`다.
   - 주문 항목은 주문 당시 상품명, 옵션, 카테고리, 개당 가격을 스냅샷으로 갖는다. 이후 catalog가 바뀌어도 그대로다([context-map.md](context-map.md) "관계").
   - 주문 금액은 항목 금액(개당 가격 × 수량)의 합이다. 배송비는 없다(무료 배송, 2026-10-20 결정). M4에서도 그대로 두고, 반품 배송비만 플랫폼 고정값으로 정했다([fulfillment.md](fulfillment.md) "환불 금액").
   - 주문명은 첫 항목의 상품명이다. 항목이 둘 이상이면 `<상품명> 외 <n>건`이다. 100자를 넘으면 상품명을 줄인다(토스 `orderName` 상한).
   - 하위 주문은 판매자가 처음 나온 순서, 항목은 요청 순서다.
- 장바구니는 주문할 때 바꾸지 않는다. 주문한 SKU는 결제가 끝나(`PAID`) 장바구니에서 뺀다(2026-10-20 결정, 아래 "결제 결과"). 재고 부족·만료·취소로 끝난 주문은 장바구니에 영향이 없다.

### 예약 결과
order는 `catalog.stock`에서 예약 결과를 받는다([messaging.md](messaging.md) "주문·결제 Saga"). M3는 쿠폰이 없어 재고 예약 결과 하나로 정한다.
| 받는 이벤트 | 주문 | 내는 이벤트 |
|---|---|---|
| `StockReserved` | `PENDING` → `AWAITING_PAYMENT` | `OrderAwaitingPayment`(orderId, buyerId, amount, orderName) |
| `StockReservationFailed` | `PENDING` → `CANCELLED`(사유 `STOCK_UNAVAILABLE`) | `OrderCancelled` |

- 리스너는 처리 기록(`processed_event`)으로 같은 이벤트를 한 번만 처리한다. 주문 행을 잠그고 상태를 바꾼다.
- `PENDING`이 아닌 주문에 결과가 오면(이미 같은 상태면 조용히, 다른 상태면 경고 로그) 넘긴다. order가 모르는 주문이면 경고 로그를 남기고 넘긴다.
- `StockAvailabilityChanged`는 받지 않는다.

### 결제 결과
order는 `payment.payment`에서 결제 결과를 받는다([payment.md](payment.md)).
| 받는 이벤트 | 주문 | 내는 이벤트 |
|---|---|---|
| `PaymentApproved` | `AWAITING_PAYMENT` → `PAID`. 결제 시각(`paidAt`)은 토스 승인 시각이다 | `OrderPaid` |
| `PaymentFailed` | 받지 않는다. 주문은 `AWAITING_PAYMENT`로 남아 만료 전까지 다시 결제할 수 있다 | |

- 리스너는 처리 기록으로 같은 이벤트를 한 번만 처리하고, 주문 행을 잠그고 바꾼다. 이미 `PAID`면 조용히 넘긴다.
- `CANCELLED` 주문(만료·구매자 취소가 먼저 됐다)에 온 승인은 경고 로그를 남기고 넘긴다. 결제는 payment가 `OrderCancelled`를 받아 보상 취소한다([messaging.md](messaging.md) "실패와 보상"). `PENDING` 주문에는 결제가 생기지 않으므로 승인이 오면 같은 경고다. 모르는 주문도 경고 로그만 남긴다.
- 승인 금액이 주문 금액과 다르면 예외를 던진다. payment가 `OrderAwaitingPayment`의 금액을 고정하므로 생기면 버그다. 재시도 뒤 DLT로 가서 운영자가 본다.
- `PAID`가 되면 같은 트랜잭션에서 구매자 장바구니를 잠그고 주문한 SKU의 줄을 뺀다. 담은 수량이 주문 수량과 달라도 줄을 뺀다. order는 주문이 장바구니에서 왔는지 상품 하나를 바로 주문했는지 구분하지 않아서, 바로 주문한 SKU도 장바구니에 있으면 빠진다. 장바구니가 없으면 아무것도 하지 않는다.
- `OrderPaid`에는 하위 주문(판매자, 금액, 항목의 SKU·상품·카테고리·개당 가격·수량·금액)과 배송지가 실린다. 필드는 [context-map.md](context-map.md) "order.order".

### 하위 주문 진행 상태(`SellerOrderProgress`)
결제된 주문의 하위 주문마다 배송 진행을 따로 갖는다(ORD-3 "판매자별 진행 상태"). fulfillment의 배송 이벤트(`fulfillment.shipment`)로 바꾼다([fulfillment.md](fulfillment.md) "배송").
| 진행 상태 | 화면 이름 | 바뀌는 때 |
|---|---|---|
| `PAID` | 결제 완료 | 주문이 결제 완료가 될 때 모든 하위 주문 |
| `ACCEPTED` | 상품 준비 중 | `ShipmentAccepted` |
| `SHIPPED` | 발송 | `ShipmentShipped` |
| `IN_TRANSIT` | 배송 중 | `ShipmentInTransit` |
| `DELIVERED` | 배송 완료 | `ShipmentDelivered` |
| `CONFIRMED` | 구매확정 | `PurchaseConfirmed` |

- 결제 전 주문의 하위 주문은 진행 상태가 없다(`null`). 화면은 주문 상태를 보여 준다.
- 앞으로만 간다. 지금보다 앞이거나 같은 단계의 이벤트(재처리, 늦게 온 이벤트)는 넘긴다. 클레임은 M4의 다음 PR에서 더한다.
- 리스너는 처리 기록으로 같은 이벤트를 한 번만 처리하고, 주문 행을 잠그고 바꾼다. 리스너 이름은 `order.shipment-<단계>`다(예: `order.shipment-accepted`). order가 모르는 주문·하위 주문이면 경고 로그만 남긴다.
- 진행 상태를 바꿔도 이벤트를 내지 않는다.

### 만료와 결제 전 취소
- **결제 기한**은 주문 시각부터 15분이다(`openmarket.order.payment-timeout`, config-repo `order.yml`). E2E만 환경 변수 `ORDER_PAYMENT_TIMEOUT`과 `ORDER_EXPIRY_INTERVAL`로 줄인다([dev-setup.md](../dev-setup.md) "E2E"). `PENDING`과 `AWAITING_PAYMENT` 모두 기한이 지나면 취소된다. 기한을 예약 결과가 온 시각이 아니라 주문 시각에서 세는 것은, catalog가 멈춰 예약 결과가 오지 않아도 `PENDING` 주문이 남지 않게 하고 구매자에게 보이는 기한을 주문할 때 정하기 위해서다.
- **만료 작업**(`OrderExpiryJob`)은 1분마다(`openmarket.order.expiry-interval`) 돌고 ShedLock으로 한 인스턴스에서만 돈다. 기한이 지난 결제 전 주문을 오래된 순으로 100건씩 읽어, 주문마다 트랜잭션을 따로 열고 `PAYMENT_TIMEOUT`으로 취소한다. 다 못 하면 다음 주기에 이어 한다. 결제 전 주문만 담는 부분 인덱스(`orders_unpaid_placed_idx`)로 찾는다.
- **구매자 취소**: `POST /v1/orders/{orderId}/cancel`. `PENDING`, `AWAITING_PAYMENT` 주문을 `BUYER_CANCELLED`로 취소한다. 204다.
  - 이미 취소된 주문이면 사유를 바꾸지 않고 204다. 다시 눌러도 결과가 같아 `Idempotency-Key`는 받지 않는다.
  - 결제가 끝난 주문은 409 `ORDER_NOT_CANCELLABLE`이다. 결제 후 취소는 클레임(M4)이다. 다른 회원의 주문은 404 `ORDER_NOT_FOUND`다.
- 만료와 구매자 취소 모두 주문 행을 잠그고 바꾸고 `OrderCancelled`를 낸다. 같은 순간 결제 승인이 끝나 `PAID`가 되었으면 만료는 넘기고(경고 로그), 구매자 취소는 409다. 결제가 진행 중인데 취소가 먼저 되면 payment가 승인 뒤 보상 취소한다([messaging.md](messaging.md) "실패와 보상").

### 취소 사유(`CancelReason`)
| 사유 | 언제 |
|---|---|
| `STOCK_UNAVAILABLE` | 재고 예약 실패 |
| `PAYMENT_TIMEOUT` | 결제 기한(주문 후 15분) 초과 |
| `BUYER_CANCELLED` | 결제 전 구매자 취소 |

### 조회
구매자(ORD-3)
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/orders?cursor=&size=` | 본인 주문 목록. 모든 상태를 최근 주문 순으로 준다(주문 시각이 같으면 주문 ID 역순). 줄은 상태, 취소 사유, 금액, 주문명, 주문 시각이다. 커서 페이징이고 `size`는 1~50(기본 20)이다. 형식이 틀린 커서는 400 `ORDER_INVALID_CURSOR` |
| GET | `/v1/orders/{orderId}` | 본인 주문 상세. 상태, 취소 사유, 금액, 주문명, 배송지, 하위 주문(판매자, 상호, 금액, 진행 상태, 항목), 주문 시각, 결제 시각(결제됐을 때만). 다른 회원의 주문은 404 `ORDER_NOT_FOUND`다. 화면은 주문을 만든 뒤 이 API로 `AWAITING_PAYMENT`가 될 때까지 기다린다 |

판매자(ORD-4). `SELLER` 역할이 있어야 하고, 주체는 토큰의 `sub`(= sellerId)다.
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/seller/orders?page=&size=` | 본인 하위 주문 목록. 최근 결제 순, 쪽 번호. `size`는 1~100(기본 20)이다. 줄은 주문 ID, 상태, 진행 상태, 하위 주문 금액, 첫 상품명, 항목 수, 주문 시각, 결제 시각이다 |
| GET | `/v1/seller/orders/{sellerOrderId}` | 본인 하위 주문 상세. 상태, 진행 상태, 금액, 배송지, 항목, 주문 시각, 결제 시각. 다른 판매자의 것이거나 보이지 않는 상태면 404 `ORDER_NOT_FOUND` |

- 판매자는 결제된(`PAID`) 주문의 하위 주문만 본다. 결제 전 주문은 판매자가 처리할 것이 없고, 재고 부족이나 만료로 사라질 수 있기 때문이다. 판매자는 다른 판매자의 하위 주문, 주문 전체 금액, 구매자 ID를 보지 않는다. 배송지는 발송에 필요해서 준다.
- 판매자 목록은 결제 시각 순이다(같으면 하위 주문 ID 역순). 판매자가 처리할 일은 결제로 생기고, 주문 후 결제까지 최대 15분이 걸려 주문 시각 순이면 새로 결제된 주문이 첫 쪽 아래에 끼어든다.
- 하위 주문의 진행 상태는 위 "하위 주문 진행 상태"다. 하위 주문 처리(발주 확인, 발송)는 fulfillment가 한다([fulfillment.md](fulfillment.md) "API").

## 내부 API
서비스 간 조회다. `internal` scope 토큰만 부를 수 있고 gateway는 넘기지 않는다. 프론트 api-client(OpenAPI 문서)에 넣지 않는다.
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/internal/v1/buyers/{buyerId}/ongoing-orders` | 구매자에게 진행 중인 주문이 있는지(`{hasOngoingOrders}`). member 탈퇴(ACC-4)가 부른다([member.md](member.md) "탈퇴") |

- 진행 중인 주문은 결제 전(`PENDING`, `AWAITING_PAYMENT`) 주문과, 구매확정되지 않은 하위 주문이 하나라도 있는 결제 완료 주문이다. 결제 전 주문은 결제가 진행 중일 수 있다. 취소된 주문과 하위 주문이 모두 구매확정된 주문은 끝난 주문이다(2026-10-21, M4).
- 클레임이 생기면 클레임으로 끝난 하위 주문을 끝난 것으로 보고, 진행 중인 클레임을 더한다(M4 클레임 PR).

## catalog 조회
order는 catalog 내부 API `GET /internal/v1/skus`로 SKU의 상품명, 판매 상태, 판매자, 가격, 판매 가능 수량을 읽는다([catalog.md](catalog.md) "내부 API"). 동기 호출은 조회에만 쓴다([overview.md](overview.md) "통신 규칙").
- **클라이언트**: OpenFeign, Eureka로 찾는다(`lb://catalog`). 서비스 간 토큰은 `client_credentials`(클라이언트 ID `order`, scope `internal`)로 받아 만료 전까지 재사용한다.
- **타임아웃**: 연결 1초, 읽기 2초. 연결이 거절됐을 때(`ConnectException`)만 한 번 더 시도한다(조회라 안전하다). 연결·읽기 타임아웃은 다시 시도하지 않는다. 사용자가 두 배를 기다리고 느린 catalog에 부하를 더하기 때문이다.
- 서킷 브레이커와 Feign 호출은 요청 스레드에서 그대로 돈다(Resilience4j의 시간 제한과 스레드 풀을 끈다). 별도 스레드로 넘기면 트레이스와 보안 컨텍스트가 끊긴다. 시간 제한은 Feign 타임아웃이 맡는다.
- **서킷 브레이커**(NFR-4, Resilience4j): 최근 20건 중 10건 이상에서 절반이 실패하면 10초 동안 catalog를 부르지 않고 바로 실패한다. 4xx는 실패로 세지 않는다. 값은 config-repo `order.yml`에 있다.
- catalog를 부를 수 없으면(타임아웃, 5xx, 서킷 열림) 503 `ORDER_CATALOG_UNAVAILABLE`이다. 장바구니 조회, 담기, 주문 생성이 이 응답을 받는다. 수량 바꾸기와 빼기는 영향이 없다.
- 커맨드(담기)와 조회(장바구니 보기)는 각자 Feign 클라이언트를 둔다. 커맨드는 `command.application`의 `SkuCatalogPort`를 `infra`가 구현하고, 조회는 `query.dao`가 직접 부른다. 커맨드와 쿼리가 모델을 공유하지 않는다는 규칙([conventions.md](conventions.md)) 때문이다.
