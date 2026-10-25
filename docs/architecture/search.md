# 검색 (search 서비스)

구매자 상품 검색(SRC-1, SRC-3)과 자동완성(SRC-2)을 맡는다. catalog의 상품 이벤트와 품절 전환 이벤트, review의 별점 요약, fulfillment의 구매확정으로 Elasticsearch 읽기 모델을 만든다. 원본 데이터는 각 서비스에 있고, search는 다른 서비스를 동기로 부르지 않는다.
Postgres `search` 스키마에는 공통 테이블(멱등 기록 `processed_event`, ShedLock 등)과 판매량 집계(`product_sales`, 아래 "판매량")만 둔다.
별점·판매량 정렬과 필터, 자동완성은 M5에서 더했다. 아래 "M5 설계(2026-10-22)"가 정한 규칙이다. 별점·판매량·정렬은 search v2 PR(2026-10-25)에서 "검색 API"에, 자동완성은 다음 PR(2026-10-25)에서 "자동완성 API"에 합쳤다.

## 인덱스
- 인덱스 `products-v2`에 별칭 `products`를 붙이고, 읽기와 쓰기는 별칭으로 한다. 매핑을 바꾸면 새 인덱스를 만들어 재색인한 뒤 별칭을 옮긴다([data.md](data.md), 아래 "인덱스 v2").
- 매핑과 분석기의 원본은 `services/core/search/src/main/resources/elasticsearch/products-v2.json`이다(v1은 `products-v1.json`으로 남긴다). search가 뜰 때 별칭이 없으면 인덱스와 별칭을 만든다.
- 한국어 텍스트(상품명, 설명, 상호)는 nori `mixed` 분석기 하나로 색인·검색한다([ADR 0016](../adr/0016-elasticsearch-nori.md)).
- 문서 하나가 상품 하나다(`검색 상품`, `SearchProduct`). 필드: productId, revision, status, sellerId, storeName, categoryId, categoryPath, name(하위 필드 `name.suggest`), description, thumbnailFileId(첫 이미지), minPrice, publishedAt, inStock, availabilityRevision, ratingAverage, ratingCount, ratingRevision, salesCount, salesRevision.

## 색인
`catalog.product`와 `catalog.stock`을 소비 그룹 `search`로 받는다. 필드와 순서 규칙은 [context-map.md](context-map.md) "catalog.product", "catalog.stock"에 있다.
| 이벤트 | 반영 |
|---|---|
| `ProductRegistered`, `ProductPublished`, `ProductChanged` | 스냅샷 전체. `availabilityRevision`이 저장된 것보다 크면 `inStock`도 바꾼다 |
| `ProductStopped`, `ProductResumed`, `ProductBanned` | 상태만 바꾼다(`STOPPED`, `ON_SALE`, `BANNED`) |
| `StockAvailabilityChanged` | `inStock`만 바꾼다 |
| `RatingSummaryChanged`(review.rating-summary) | 별점만 바꾼다(아래 "별점") |
| `PurchaseConfirmed`(fulfillment.shipment) | 판매량을 더한다(아래 "판매량") |

- 리스너 이름은 `search.rating-summary-changed`, `search.purchase-confirmed`다. 두 토픽의 다른 이벤트는 `type` 헤더로 넘긴다.

- 상품 이벤트는 상품 `revision`이, 품절 전환은 `availabilityRevision`이 저장된 것보다 클 때만 반영한다. 두 토픽 사이 순서가 섞여도 같은 결과가 된다.
- 아직 모르는 상품의 `StockAvailabilityChanged`는 판매 가능 여부만 가진 문서를 만들어 둔다. 상태가 없으므로 검색에는 나오지 않는다.
- 문서를 읽고 고친 뒤 읽은 버전(`_seq_no`, `_primary_term`)이 그대로일 때만 쓴다. 두 토픽의 리스너가 같은 문서를 동시에 고치면 한쪽이 충돌하고 다시 읽어 반영한다.
- Elasticsearch 쓰기는 DB 트랜잭션에 묶이지 않는다. 쓰기 뒤 멱등 기록이 실패해 다시 처리해도 리비전 검사로 결과가 같다.
- 판매 시작이나 수정이 검색에 보이기까지는 Debezium 전달과 색인 새로 고침(1초)을 더해 몇 초다.

## 검색 API
gateway 경로는 `/api/search` + 아래 경로다. 토큰 없이 연다(gateway와 search의 `public-paths`).

`GET /v1/products?q=&categoryId=&minPrice=&maxPrice=&minRating=&sort=&cursor=&size=`
| 파라미터 | 규칙 |
|---|---|
| `q` | 선택, 1~100자. 상품명(가중치 3), 상호, 설명에서 찾는다. 검색어의 모든 단어가 있어야 한다(`operator: and`, 필드를 가로질러 `cross_fields`) |
| `categoryId` | 선택. 대분류를 주면 그 아래 소분류 상품도 나온다(`categoryPath`) |
| `minPrice`, `maxPrice` | 선택, 0 이상. 상품 최저가(`minPrice`)로 거른다 |
| `minRating` | 선택, 1~5. 평균 별점이 그 이상인 상품만. 리뷰가 없는 상품은 빠진다 |
| `sort` | `RELEVANCE`(기본), `LATEST`(판매 시작 최신), `PRICE_ASC`, `PRICE_DESC`, `RATING`(평균 별점 높은 순, 같으면 리뷰 수 많은 순, 리뷰 없는 상품은 뒤), `SALES`(판매량 많은 순). 검색어가 없으면 `RELEVANCE`는 `LATEST`와 같다. `RELEVANCE`는 점수가 같으면 판매량 많은 순, 그다음 판매 시작 최신 순이다 |
| `cursor`, `size` | 커서 페이징([conventions.md](conventions.md) "REST API"). `size` 1~100(기본 20). 응답의 `nextCursor`를 다음 요청에 넣는다. 마지막 쪽이면 `null` |

- 응답: `{items: [{productId, name, storeName, minPrice, inStock, thumbnailFileId, publishedAt, ratingAverage, ratingCount}], totalCount, nextCursor}`. 이미지는 `/files/{thumbnailFileId}/w300`으로 받는다([file.md](file.md)). 리뷰가 없으면 `ratingAverage`는 null, `ratingCount`는 0이다(REV-2).
- 판매 중(`ON_SALE`)인 상품만 나온다. 판매 중지·제재·등록 중 상품은 빠진다(SRC-3).
- 품절 상품(`inStock=false`)은 어떤 정렬에서도 판매 가능한 상품 뒤에 온다(SRC-3). 같은 값이면 productId 순이다.
- 커서는 마지막 결과의 정렬 값을 담은 불투명한 문자열이다(`search_after`). 형식이 틀리면 400 `SEARCH_INVALID_CURSOR`다.
- `totalCount`는 1만 건까지 정확하고, 넘으면 10000이다(Elasticsearch 기본 `track_total_hits`). 정확히 세려면 결과가 많을 때 모든 문서를 훑어야 해서 세지 않는다. 결과는 커서로 끝까지 넘길 수 있다.

## 자동완성 API
`GET /v1/suggestions?q=` (SRC-2). 토큰 없이 연다(gateway와 search의 `public-paths`).
- `q`: 필수, 1~50자. 앞뒤 공백을 지운다. 공백뿐이면 빈 목록이다.
- 판매 중(`ON_SALE`)인 상품의 `name.suggest`에서 찾는다. 검색어의 모든 단어가 상품명 어느 단어의 앞부분이어야 한다(`match`, `operator: and`). 예: `등산 가` → `등산 가방 30L`. 단어 가운데(`방` → `가방`)는 맞지 않는다. 다만 nori `mixed`가 복합어를 나눈 하위 단어도 단어로 본다(`취` → `토스부분취소확인`의 `취소`).
- 순서: 판매량 많은 순 → 관련도 → 판매 시작 최신 순 → productId. 품절 상품도 나온다(검색 결과에서는 뒤로 가지만 있는 상품이다).
- 같은 상품명은 하나만 준다. `name`은 text라 Elasticsearch에서 상품명으로 묶을 수 없어(`collapse`에는 keyword 필드가 필요하다) 후보 50개를 받아 앞에서부터 겹치는 이름을 거른다. 후보 안에 같은 이름이 많으면 10개보다 적을 수 있다. 인덱스를 또 바꿀 만한 문제가 아니라고 보고 keyword 하위 필드는 더하지 않았다.
- 응답: `{items: [{name}]}`, 최대 10개. 페이징하지 않는다.

## M5 설계(2026-10-22)
### 인덱스 v2
- 자동완성에 새 분석기(edge n-gram)가 필요하다. 열린 인덱스에는 분석기를 더할 수 없어 `products-v2`를 만든다. v1의 필드는 그대로 두고 아래 필드를 더한다.
  | 필드 | 뜻 |
  |---|---|
  | `ratingAverage`, `ratingCount`, `ratingRevision` | 별점 요약(review `RatingSummaryChanged`). 리뷰가 없으면 평균 없음, 수 0 |
  | `salesCount`, `salesRevision` | 구매확정 수량의 합(아래 "판매량") |
  | `name.suggest` | 상품명의 자동완성 하위 필드(아래 "자동완성") |
- 전환: search가 뜰 때 별칭이 v1을 가리키고 v2가 없으면, 리스너를 띄우기 전에 v2를 만들고 v1을 `_reindex`로 옮긴 뒤 별칭을 한 요청으로 v2로 옮긴다. 리스너가 아직 돌지 않으므로 옮기는 동안 쓰기가 없다. search 인스턴스는 하나라는 전제다(로컬). v1은 지우지 않고 남겨 둔다(되돌릴 때 별칭만 옮긴다).
- 새 필드는 재색인 뒤 처음 받은 이벤트부터 채워진다. 리뷰와 구매확정은 M5에 처음 생기므로 비어 있는 값이 맞다. v1에서 옮긴 문서는 새 필드가 없고, 읽을 때 0(평균은 없음)으로 본다.
- 구현(2026-10-25): `ProductIndexMigration`이 별칭이 가리키는 인덱스를 보고 만들기·그대로·옮기기 중 하나를 한다. 재색인은 새로 고침까지 기다리고(`refresh`, `wait_for_completion`), 실패한 문서가 있으면 뜨지 않는다. 별칭 없이 남은 v2(전에 옮기다 멈춤)는 지우고 다시 만든다. 다른 이름으로 같은 절차를 돌리는 통합 테스트가 있다.
- `name.suggest`의 분석기도 v2에 함께 넣었다(색인 `korean_suggest` = nori `mixed` + edge n-gram 1~20, 검색 `korean`). 자동완성 API를 더할 때 인덱스를 또 바꾸지 않기 위해서다.

### 별점
- `review.rating-summary`를 소비 그룹 `search`로 받는다. 리비전(`ratingRevision`)이 저장된 것보다 클 때만 반영한다. 아직 모르는 상품이면 별점만 가진 문서를 만든다(품절 전환과 같다. 상태가 없으므로 검색에 나오지 않는다).

### 판매량
- `fulfillment.shipment`의 `PurchaseConfirmed`를 받아 항목의 상품별 수량을 더한다. 구매확정 뒤에는 클레임이 없어 빼는 일이 없다(2026-10-22 결정).
- 같은 구매확정이 다른 이벤트 ID로 다시 오면 처리 기록으로 거를 수 없어 두 번 더한다. 그래서 하위 주문 ID를 `product_sales_order`에 넣고(`ON CONFLICT DO NOTHING`) 이미 있으면 넘긴다(review·settlement가 기본 키로 거르는 것과 같다).
- 더하기는 `INSERT ... ON CONFLICT (product_id) DO UPDATE ... RETURNING sales_count, revision` 한 문장이다. 같은 상품을 동시에 더하면 한쪽이 커밋까지 기다린다. 한 구매확정의 여러 상품은 같은 상품을 합친 뒤 상품 ID 순으로 더한다(교착 방지).
- 더하기는 다시 처리하면 두 번 더해지므로 Elasticsearch 문서에서 하지 않는다. `IdempotentEventProcessor` 트랜잭션 안에서 `product_sales`(상품, 판매량, 리비전) 행을 잠그고 더한 뒤, 커밋하기 전에 더한 결과(절대값)와 리비전을 문서에 쓴다. 문서 쓰기가 실패하면 트랜잭션이 되돌아가 다시 처리한다. 쓰기 뒤 커밋이 실패해도 다시 처리한 값이 같다.

### 정렬과 필터
- `sort`에 `RATING`(평균 별점 높은 순, 같으면 리뷰 수 많은 순)과 `SALES`(판매량 많은 순)를 더한다. 리뷰가 없는 상품은 `RATING`에서 맨 뒤다. 품절 상품을 뒤로 보내는 규칙(SRC-3)은 그대로 먼저 적용한다.
- `minRating`(1~5) 필터를 더한다. 평균 별점이 그 이상인 상품만 나온다.
- `RELEVANCE`에 판매량을 약하게 더할지(`function_score`)와 그 가중치는 search PR에서 실측해 정한다.
  - 결정(2026-10-25): 점수에 더하지 않는다. 로컬에는 실제 판매 분포가 없어 가중치를 정할 근거가 없고, 판매량으로 보고 싶으면 `SALES` 정렬이 있다. 대신 점수가 같은 상품(같은 상품명 등)은 판매량 많은 순으로 둔다. 실제에 가까운 데이터가 생기는 M6 부하 테스트에서 다시 본다.
- 응답 항목에 `ratingAverage`, `ratingCount`를 더한다(REV-2).

### 자동완성(SRC-2)
- `GET /v1/suggestions?q=`: `q`는 1~50자. 판매 중인 상품의 상품명 중 검색어로 시작하는 단어가 있는 것을 최대 10개, 판매량 많은 순으로 준다. 같은 상품명은 하나만 준다.
- `name.suggest`는 nori로 단어를 나눈 뒤 단어마다 앞에서부터 1~20글자를 색인한다(edge n-gram). 검색어는 n-gram 없이 같은 토크나이저로 나눈다.
- 완성된 글자 단위로 맞춘다. 자모 단위(`사ㄱ` → `사과`)와 초성 검색은 하지 않는다.
- shop 검색창이 입력을 멈추고 잠깐 뒤(디바운스)에 BFF로 부른다. 지연 시간과 BFF 캐시는 shop PR에서 정한다.
- 구현(2026-10-25): 위 "자동완성 API". 같은 상품명 거르기는 후보 50개 안에서 한다.

