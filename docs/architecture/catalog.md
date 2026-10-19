# 상품 (catalog 서비스)

카테고리, 상품, SKU, 재고를 갖는다(CAT-1~5). 구매자 목록·검색은 search가 이벤트로 만든 읽기 모델로 한다([context-map.md](context-map.md) "catalog.product").
이 문서는 M2의 등록·수정·판매 시작까지를 다룬다. 판매 중지·재개, 관리자 제재, 판매자 정지 반영, 재고 조정은 다음 PR에서 더한다.

## 카테고리
- 2단계 트리다. 대분류 8개, 소분류 24개를 Flyway(`V1__catalog.sql`)로 넣는다. 관리 화면과 API는 없고 바꿀 때도 마이그레이션으로 바꾼다(2026-10-19 결정).
- 상품은 소분류에만 붙는다. ID는 고정이다. 정산 수수료율(M5)이 카테고리 ID를 쓴다.

## 판매자 사본
- catalog는 member의 `SellerApproved`를 받아 판매자 사본(`seller`: 상호, 상태)을 만든다. 상품 스냅샷과 상세에 상호를 넣는 데 쓴다.
- 승인 직후 사본이 아직 없으면 상품 등록은 409 `CATALOG_SELLER_NOT_READY`다. 화면은 잠시 뒤 다시 부른다.
- 토픽 보관 기간(7일)보다 오래전에 승인된 판매자는 catalog를 처음 띄울 때 사본이 생기지 않는다. 로컬 개발 데이터에서만 생기는 일이라, 그때는 `docker compose down -v`로 다시 시작한다.

## 상품
상태는 `DRAFT`(등록) → `ON_SALE`(판매 시작)이다. `STOPPED`, `BANNED`는 판매 상태 기능에서 더한다.
1. 판매자가 file에 이미지를 올리고([file.md](file.md)) 받은 `fileId`로 상품을 등록한다. 상품은 `DRAFT`이고, SKU마다 초기 재고로 `Stock`을 만든다. `ProductRegistered`가 나간다.
2. 판매를 시작하면 `ON_SALE`이 되고 `ProductPublished`가 나간다. search가 받아 검색에 넣는다.
3. 수정은 상태와 상관없이 할 수 있고 `ProductChanged`가 나간다.

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
- 이미지 `fileId`가 실제로 그 판매자가 올린 파일인지는 catalog가 확인하지 않는다. file이 첨부를 확정할 때 업로더와 판매자가 같은 파일만 붙인다(첨부 PR).
- 다른 판매자의 상품은 없는 상품처럼 404 `CATALOG_PRODUCT_NOT_FOUND`다.
- 동시성: 수정과 판매 시작은 상품 행을 잠그고(`SELECT ... FOR UPDATE`) 한다. 이벤트마다 올리는 `revision`이 이벤트 순서와 같다.

### 이벤트
`ProductRegistered`, `ProductPublished`, `ProductChanged`는 같은 상품 스냅샷을 페이로드로 낸다(필드는 [context-map.md](context-map.md)). JSON 예시는 `services/core/catalog/src/test/resources/events/`에 있다.
- 스냅샷에는 SKU별 재고 수량 대신 판매 가능 여부(`inStock`)만 넣는다.
- 등록에도 이벤트를 내는 이유: file이 초안의 이미지도 첨부를 확정해야 24시간 정리에서 지워지지 않는다.

## API
gateway 경로는 `/api/catalog` + 아래 경로다. 판매자 API의 주체는 토큰의 `sub`(= sellerId)다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/categories` | 카테고리 트리. 공개 |
| GET | `/v1/products/{id}` | 구매자 상품 상세. 공개. 판매 중·판매 중지 상품만 보이고, 남은 수량 대신 SKU별 `inStock`을 준다 |
| GET | `/v1/seller/products?status=&page=&size=` | 본인 상품 목록. 최근 수정 순, 쪽 번호 `{items, totalCount, page, size}` |
| GET | `/v1/seller/products/{id}` | 본인 상품 상세. SKU별 실재고·예약·판매 가능 수량 |
| POST | `/v1/seller/products` | 등록(`DRAFT`). `Idempotency-Key` 필수. 201 `{productId}` |
| PUT | `/v1/seller/products/{id}` | 수정. 204 |
| POST | `/v1/seller/products/{id}/publish` | 판매 시작. 이미 판매 중이면 204. 이미지가 없으면 422 `CATALOG_PRODUCT_NOT_PUBLISHABLE` |

- 이미지는 `fileId`로만 준다. 화면은 `/files/{fileId}/w300`, `/files/{fileId}/w800`으로 받는다([file.md](file.md)).
- 공개 경로는 gateway와 catalog의 `public-paths`에 같이 적는다([auth.md](auth.md) "인가").
