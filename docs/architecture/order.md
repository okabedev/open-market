# 주문 (order 서비스)

장바구니, 주문과 하위 주문, 주문 만료를 갖는다(ORD-1~4). 주문 생성·만료·취소와 결제 연동은 M3의 다음 PR에서 이 문서에 더한다. 주문 흐름 전체(Saga)는 [messaging.md](messaging.md)에 있다.

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

## catalog 조회
order는 catalog 내부 API `GET /internal/v1/skus`로 SKU의 상품명, 판매 상태, 판매자, 가격, 판매 가능 수량을 읽는다([catalog.md](catalog.md) "내부 API"). 동기 호출은 조회에만 쓴다([overview.md](overview.md) "통신 규칙").
- **클라이언트**: OpenFeign, Eureka로 찾는다(`lb://catalog`). 서비스 간 토큰은 `client_credentials`(클라이언트 ID `order`, scope `internal`)로 받아 만료 전까지 재사용한다.
- **타임아웃**: 연결 1초, 읽기 2초. 연결이 거절됐을 때(`ConnectException`)만 한 번 더 시도한다(조회라 안전하다). 연결·읽기 타임아웃은 다시 시도하지 않는다. 사용자가 두 배를 기다리고 느린 catalog에 부하를 더하기 때문이다.
- 서킷 브레이커와 Feign 호출은 요청 스레드에서 그대로 돈다(Resilience4j의 시간 제한과 스레드 풀을 끈다). 별도 스레드로 넘기면 트레이스와 보안 컨텍스트가 끊긴다. 시간 제한은 Feign 타임아웃이 맡는다.
- **서킷 브레이커**(NFR-4, Resilience4j): 최근 20건 중 10건 이상에서 절반이 실패하면 10초 동안 catalog를 부르지 않고 바로 실패한다. 4xx는 실패로 세지 않는다. 값은 config-repo `order.yml`에 있다.
- catalog를 부를 수 없으면(타임아웃, 5xx, 서킷 열림) 503 `ORDER_CATALOG_UNAVAILABLE`이다. 장바구니 조회와 담기가 이 응답을 받는다. 수량 바꾸기와 빼기는 영향이 없다.
- 커맨드(담기)와 조회(장바구니 보기)는 각자 Feign 클라이언트를 둔다. 커맨드는 `command.application`의 `SkuCatalogPort`를 `infra`가 구현하고, 조회는 `query.dao`가 직접 부른다. 커맨드와 쿼리가 모델을 공유하지 않는다는 규칙([conventions.md](conventions.md)) 때문이다.
