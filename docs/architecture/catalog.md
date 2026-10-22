# 상품 (catalog 서비스)

카테고리, 상품, SKU, 재고를 갖는다(CAT-1~5). 구매자 목록·검색은 search가 이벤트로 만든 읽기 모델로 한다([context-map.md](context-map.md) "catalog.product", "catalog.stock").

## 카테고리
- 2단계 트리다. 대분류 8개, 소분류 24개를 Flyway(`V1__catalog.sql`)로 넣는다. 관리 화면과 API는 없고 바꿀 때도 마이그레이션으로 바꾼다(2026-10-19 결정).
- 상품은 소분류에만 붙는다. ID는 고정이다. 정산 수수료율(M5)이 카테고리 ID를 쓴다.

## 판매자 사본
- catalog는 member의 `SellerApproved`를 받아 판매자 사본(`seller`: 상호, 상태)을 만든다. 상품 스냅샷과 상세에 상호를 넣는 데 쓴다.
- 승인 직후 사본이 아직 없으면 상품 등록은 409 `CATALOG_SELLER_NOT_READY`다. 화면은 잠시 뒤 다시 부른다.
- `SellerSuspended`, `SellerReinstated`로 사본의 상태를 바꾸고 상품 판매 상태를 맞춘다(아래 "판매 상태").
- 토픽 보관 기간(7일)보다 오래전에 승인된 판매자는 catalog를 처음 띄울 때 사본이 생기지 않는다. 로컬 개발 데이터에서만 생기는 일이라, 그때는 `docker compose down -v`로 다시 시작한다.

## 상품
상태는 `DRAFT`(등록) → `ON_SALE`(판매 시작) ↔ `STOPPED`(판매 중지), 관리자 제재 시 `BANNED`다(아래 "판매 상태").
1. 판매자가 file에 이미지를 올리고([file.md](file.md)) 받은 `fileId`로 상품을 등록한다. 상품은 `DRAFT`이고, SKU마다 초기 재고로 `Stock`을 만든다. `ProductRegistered`가 나간다.
2. 판매를 시작하면 `ON_SALE`이 되고 `ProductPublished`가 나간다. search가 받아 검색에 넣는다.
3. 수정은 제재된 상품이 아니면 상태와 상관없이 할 수 있고 `ProductChanged`가 나간다.

### 규칙
입력 규칙을 어기면 400 `CATALOG_INVALID_INPUT`이다. 메시지에 입력값을 넣지 않는다. 앞뒤 공백은 지운다.
| 항목 | 규칙 |
|---|---|
| 이름 | 1~100자 |
| 설명 | 1~5,000자, 일반 텍스트 |
| 카테고리 | 있는 소분류 |
| 이미지 | 서로 다른 `fileId` 10장까지. 판매를 시작하려면 1장 이상 |
| 옵션 이름 | 서로 다른 이름 2개까지, 1~20자. 등록할 때 정하고 바꾸지 않는다 |
| 옵션 조합(SKU) | 1~100개. 옵션 이름 수만큼 값(1~30자)을 갖고 조합이 겹치지 않는다. 옵션이 없으면 SKU 하나 |
| 가격 | 100원~1억 원, 10원 단위 |
| 초기 재고 | 0~999,999 |

- 수정에서는 기존 SKU를 모두 다시 보내야 한다. 기존 SKU는 가격만 바꿀 수 있고 옵션 값은 바꿀 수 없다. `skuId` 없는 줄은 새 SKU이고 초기 재고를 함께 받는다.
- SKU 삭제와 옵션 구성 변경은 M2에서 받지 않는다. 팔지 않으려면 상품 판매를 중지한다. SKU를 지우면 주문(M3)이 가리키는 SKU가 사라지기 때문이다.
- 이미지 `fileId`가 실제로 그 판매자가 올린 파일인지는 catalog가 확인하지 않는다. file이 첨부를 확정할 때 업로더와 판매자가 같은 파일만 붙인다([file.md](file.md) "첨부").
- 다른 판매자의 상품은 없는 상품처럼 404 `CATALOG_PRODUCT_NOT_FOUND`다.
- 동시성: 수정과 판매 시작은 상품 행을 잠그고(`SELECT ... FOR UPDATE`) 한다. 이벤트마다 올리는 `revision`이 이벤트 순서와 같다.

### 이벤트
`ProductRegistered`, `ProductPublished`, `ProductChanged`는 같은 상품 스냅샷을 페이로드로 낸다(필드는 [context-map.md](context-map.md)). JSON 예시는 `services/core/catalog/src/test/resources/events/`에 있다.
- 스냅샷에는 SKU별 재고 수량 대신 판매 가능 여부(`inStock`)만 넣는다. 상품 단위 `inStock`은 판매 가능 여부(아래 "재고")와 그 리비전을 함께 넣는다.
- 등록에도 이벤트를 내는 이유: file이 초안의 이미지도 첨부를 확정해야 24시간 정리에서 지워지지 않는다.

## 판매 상태
| 동작 | 누가 | 전이 | 이벤트 | 안 되는 경우(422) |
|---|---|---|---|---|
| 판매 중지 | 판매자 | `ON_SALE` → `STOPPED`(사유 `SELLER`) | `ProductStopped` | 등록 중 `CATALOG_INVALID_STATUS`, 제재 `CATALOG_PRODUCT_BANNED` |
| 판매 재개 | 판매자 | `STOPPED`(사유 `SELLER`) → `ON_SALE` | `ProductResumed` | 판매자 정지 중이거나 정지로 멈춘 상품 `CATALOG_SELLER_SUSPENDED`, 제재 `CATALOG_PRODUCT_BANNED`, 등록 중 `CATALOG_INVALID_STATUS` |
| 판매자 정지 | member `SellerSuspended` | 판매 중인 상품 모두 → `STOPPED`(사유 `SELLER_SUSPENDED`) | 상품마다 `ProductStopped` | |
| 정지 해제 | member `SellerReinstated` | 사유 `SELLER_SUSPENDED`인 상품만 → `ON_SALE` | 상품마다 `ProductResumed` | |
| 제재 | 관리자 | 어떤 상태든 → `BANNED`(사유 필수, 500자) | `ProductBanned` | |

- 같은 동작을 다시 하면 아무것도 바꾸지 않고 204다. 이벤트도 다시 나가지 않는다.
- 판매자가 직접 멈춘 상품은 판매자 정지가 와도 사유가 `SELLER`로 남는다. 정지가 풀려도 그대로 멈춰 있다.
- 판매자 정지 중에는 등록·판매 시작·재개가 422 `CATALOG_SELLER_SUSPENDED`다. 정지되면 auth가 `SELLER` 역할도 떼므로 보통은 토큰 갱신 뒤 403이 먼저다.
- 제재된 상품은 구매자에게 보이지 않고(404), 판매자는 재개·수정·판매 시작을 할 수 없다(CAT-5). 판매자 상세에는 제재 사유가 보인다. 제재 해제는 PRD에 없어 두지 않는다.
- 제재는 감사 로그(`PRODUCT_BANNED`, 관리자 ID, 사유)를 남긴다(NFR-8).
- 판매자 정지·해제는 그 판매자의 상품을 한 트랜잭션에서 모두 잠그고 바꾼다. 판매자 한 명의 상품이 아주 많아지면 나눠 처리하는 방법을 M6 부하 테스트에서 본다.

## 재고
SKU마다 `Stock`(실재고 `onHand`, 예약 `reserved`)이 있다. 판매 가능 수량은 `onHand - reserved`다. 예약은 주문이 만든다(아래 "재고 예약").

### 재고 조정(CAT-3)
판매자가 SKU의 실재고를 늘리거나 줄인다. 증감 수량(`delta`)으로 받아 동시에 조정해도 서로 덮어쓰지 않는다.
| 항목 | 규칙 |
|---|---|
| 증감 수량 | 0이 아닌 -999,999~999,999. 어기면 400 `CATALOG_INVALID_INPUT` |
| 사유 | 선택, 100자까지. 앞뒤 공백은 지우고 비면 없음 |
| 조정 뒤 실재고 | 예약 수량 이상, 999,999 이하. 어기면 422 `CATALOG_STOCK_OUT_OF_RANGE` |

- 조정은 재고 행을 잠그고(`SELECT ... FOR UPDATE`) 하고, 이력(`StockAdjustment`: 증감 수량, 조정 뒤 실재고, 사유, 시각)을 같은 트랜잭션에 남긴다.
- 같은 요청이 두 번 반영되지 않도록 `Idempotency-Key`가 필요하다.
- 제재된 상품은 조정할 수 없다(422 `CATALOG_PRODUCT_BANNED`). 그 밖의 상태에서는 할 수 있다. 판매 시작 전에 수량을 맞출 수 있어야 하기 때문이다.
- 다른 판매자의 상품이면 404 `CATALOG_PRODUCT_NOT_FOUND`, 상품에 없는 SKU면 404 `CATALOG_SKU_NOT_FOUND`다.

### 판매 가능 여부와 품절 전환 이벤트
- 상품마다 판매 가능 여부(`ProductAvailability`: `inStock`, 판매 가능 리비전)를 한 행으로 둔다. 상품을 등록할 때 리비전 1로 만들고, 그 값은 `ProductRegistered` 스냅샷에 들어간다.
- 재고 조정이나 새 SKU 추가로 SKU의 판매 가능 수량이 바뀌면, 판매 가능 여부 행을 잠그고 그 상품의 재고를 다시 읽어 `inStock`을 계산한다. 값이 바뀌었을 때만 리비전을 올리고 `catalog.stock`에 `StockAvailabilityChanged`를 낸다. search가 품절 상품을 뒤로 보낸다(SRC-3).
- 행을 잠근 뒤 재고를 읽으므로, 같은 상품의 다른 SKU를 동시에 조정해도 마지막 트랜잭션이 모든 변경을 본다. 잠그는 순서는 재고 → 판매 가능 여부다. 상품 행은 잠그지 않는다. 상품 수정과 재고 조정이 서로 기다리지 않는다.
- 상품 이벤트와 토픽이 달라 순서가 섞일 수 있다. 소비자가 맞추는 방법은 [context-map.md](context-map.md) "catalog.stock"에 있다.

## 재고 예약(CAT-4)
주문 Saga에서 catalog가 맡는 부분이다([messaging.md](messaging.md) "주문·결제 Saga"). `order.order` 토픽을 받는다.

| 받는 이벤트 | 처리 | 내는 이벤트(`catalog.stock`, 키 orderId) |
|---|---|---|
| `OrderPlaced` | 주문의 SKU를 모두 예약한다. 하나라도 모자라면 아무것도 예약하지 않는다 | `StockReserved` 또는 `StockReservationFailed`(모자라거나 없는 SKU 목록) |
| `OrderPaid` | 예약을 확정한다. 실재고와 예약 수량을 함께 줄인다. 판매 가능 수량은 그대로다 | 없음 |
| `OrderCancelled` | 예약을 푼다. 예약 수량을 줄인다 | 없음 |

- 주문 하나에 예약(`StockReservation`) 하나다. 상태는 `RESERVED` → `COMMITTED` 또는 `RELEASED`이고, 예약하지 못한 주문은 `FAILED`로 남긴다. 줄(`StockReservationLine`)은 SKU, 상품, 수량이다. 같은 SKU가 여러 줄이면 수량을 합친다.
- **초과 판매 방지**: 예약은 주문의 SKU 재고 행을 skuId 순서로 잠그고(`SELECT ... FOR UPDATE`) 판매 가능 수량을 확인한 뒤 예약 수량을 올린다. 한 트랜잭션이라 동시에 들어온 주문이 같은 수량을 두 번 가져가지 못한다. 잠그는 순서를 정해 두어 SKU가 겹치는 주문끼리 교착하지 않는다. 재고 조정과도 같은 행 잠금을 쓴다.
- 재고 행이 없는 SKU(catalog에 없는 SKU)도 모자란 것으로 본다. 상품의 판매 상태는 보지 않는다. 판매 상태는 order가 주문을 만들 때 확인한다.
- 예약·확정·해제로 SKU의 판매 가능 수량이 바뀌면 판매 가능 여부를 다시 계산하고, 바뀌었으면 `StockAvailabilityChanged`를 낸다(위 "판매 가능 여부와 품절 전환 이벤트"). 상품이 여럿이면 productId 순서로 잠근다. 잠그는 순서는 재고 → 판매 가능 여부로 재고 조정과 같다.
- **멱등과 순서**: 리스너는 처리 기록(`processed_event`)으로 같은 이벤트를 한 번만 처리한다. 그와 별도로 주문마다 예약 행이 하나라 같은 주문의 `OrderPlaced`가 다른 이벤트 ID로 다시 와도 다시 예약하지 않는다.
  - 예약이 없는 주문에 `OrderCancelled`가 오면 빈 `RELEASED` 예약을 남긴다. DLT에서 다시 보낸 `OrderPlaced`가 취소 뒤에 와도 재고를 잡지 않게 하기 위해서다.
  - 상태가 맞지 않는 이벤트(예: `RELEASED` 예약에 `OrderPaid`, 예약이 없는 주문에 `OrderPaid`)는 경고 로그를 남기고 무시한다. 같은 상태로 가는 이벤트(이미 `COMMITTED`인데 `OrderPaid`)는 조용히 넘긴다.
- 재고 조정은 실재고를 예약 수량보다 작게 만들 수 없다(위 "재고 조정"). 그래서 예약한 수량은 확정하거나 풀 때까지 남아 있다.

## 클레임 재입고
결제 후 클레임(발송 전 취소, 재입고를 고른 반품)이 끝나면 돌아온 수량을 실재고에 더한다. `fulfillment.claim`의 `ClaimCompleted`를 받는다([fulfillment.md](fulfillment.md) "클레임").
- 재입고 항목(`restockLines`: skuId, quantity)이 비어 있으면 아무것도 하지 않는다(교환, 재입고하지 않는 반품).
- SKU 재고 행을 skuId 순서로 잠그고 실재고를 늘린다. 재고 조정 이력(`StockAdjustment`)에 클레임 ID와 사유 "클레임 재입고"로 남는다. 판매자가 재고 조정 이력에서 본다.
- 클레임과 SKU마다 이력이 하나다(부분 유일 인덱스 `stock_adjustment_claim_idx`). 이미 재입고한 클레임이 다른 이벤트 ID로 다시 와도 두 번 더하지 않는다.
- 실재고 상한(999,999)을 넘으면 상한까지만 더한다. 없는 SKU(삭제된 상품)는 경고 로그를 남기고 넘긴다. 제재된 상품도 재입고한다. 실물이 돌아왔기 때문이다.
- 판매 가능 수량이 바뀌므로 판매 가능 여부를 다시 계산하고, 바뀌었으면 `StockAvailabilityChanged`를 낸다(위 "판매 가능 여부와 품절 전환 이벤트"). 잠그는 순서는 재고 → 판매 가능 여부다.
- 리스너 이름은 `catalog.claim-completed`다.

## API
gateway 경로는 `/api/catalog` + 아래 경로다. 판매자 API의 주체는 토큰의 `sub`(= sellerId)다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/categories` | 카테고리 트리. 공개 |
| GET | `/v1/products/{id}` | 구매자 상품 상세. 공개. 판매 중·판매 중지 상품만 보이고, 남은 수량 대신 SKU별 `inStock`을 준다 |
| GET | `/v1/seller/products?status=&page=&size=` | 본인 상품 목록. 최근 수정 순, 쪽 번호 `{items, totalCount, page, size}` |
| GET | `/v1/seller/products/{id}` | 본인 상품 상세. SKU별 실재고·예약·판매 가능 수량, 판매 중지 사유, 제재 사유 |
| POST | `/v1/seller/products` | 등록(`DRAFT`). `Idempotency-Key` 필수. 201 `{productId}` |
| PUT | `/v1/seller/products/{id}` | 수정. 204 |
| POST | `/v1/seller/products/{id}/publish` | 판매 시작. 이미 판매 중이면 204. 이미지가 없으면 422 `CATALOG_PRODUCT_NOT_PUBLISHABLE` |
| POST | `/v1/seller/products/{id}/stop` | 판매 중지. 204 |
| POST | `/v1/seller/products/{id}/resume` | 판매 재개. 204 |
| POST | `/v1/seller/products/{id}/skus/{skuId}/stock-adjustments` | 재고 조정. 본문 `{"delta", "reason"}`. `Idempotency-Key` 필수. 201 `{adjustmentId, onHand, reserved, available}` |
| GET | `/v1/seller/products/{id}/skus/{skuId}/stock-adjustments?page=&size=` | 재고 조정 이력. 최근 순, 쪽 번호 |
| GET | `/v1/admin/products?status=&sellerId=&page=&size=` | 관리자 상품 목록(최근 수정 순, 쪽 번호). `ADMIN` |
| POST | `/v1/admin/products/{id}/ban` | 제재. 본문 `{"reason"}`. 204 |

- 이미지는 `fileId`로만 준다. 화면은 `/files/{fileId}/w300`, `/files/{fileId}/w800`으로 받는다([file.md](file.md)).
- 공개 경로는 gateway와 catalog의 `public-paths`에 같이 적는다([auth.md](auth.md) "인가").

### 내부 API
서비스 간 토큰(`internal` scope)으로만 부른다. gateway는 넘기지 않고, OpenAPI 문서(프론트 `api-client`)에도 넣지 않는다([auth.md](auth.md) "인가" 3).
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/internal/v1/skus?ids=` | SKU 일괄 조회(order용). 쉼표로 구분한 1~100개, 어기면 400 `CATALOG_INVALID_INPUT`. SKU마다 상품 ID·이름·상태, 판매자 ID·상호, 카테고리 ID, 옵션 이름·값, 가격, 판매 가능 수량, 대표 이미지를 준다. 상품 상태와 상관없이 있는 SKU는 모두 주고, 없는 SKU는 빠진다. 주문할 수 있는지(`ON_SALE`, 수량)는 order가 판단한다 |
