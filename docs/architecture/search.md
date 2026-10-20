# 검색 (search 서비스)

구매자 상품 검색(SRC-1, SRC-3)을 맡는다. catalog의 상품 이벤트와 품절 전환 이벤트로 Elasticsearch 읽기 모델을 만든다. 원본 데이터는 catalog에 있고, search는 다른 서비스를 동기로 부르지 않는다.
Postgres `search` 스키마에는 공통 테이블(멱등 기록 `processed_event`, ShedLock 등)만 둔다.
별점·판매량 필터와 정렬, 자동완성(SRC-2)은 M5에서 더한다([roadmap.md](../roadmap.md) "M2").

## 인덱스
- 인덱스 `products-v1`에 별칭 `products`를 붙이고, 읽기와 쓰기는 별칭으로 한다. 매핑을 바꾸면 새 인덱스(`products-v2`)를 만들어 재색인한 뒤 별칭을 옮긴다([data.md](data.md)).
- 매핑과 분석기의 원본은 `services/core/search/src/main/resources/elasticsearch/products-v1.json`이다. search가 뜰 때 별칭이 없으면 인덱스와 별칭을 만든다.
- 한국어 텍스트(상품명, 설명, 상호)는 nori `mixed` 분석기 하나로 색인·검색한다([ADR 0016](../adr/0016-elasticsearch-nori.md)).
- 문서 하나가 상품 하나다(`검색 상품`, `SearchProduct`). 필드: productId, revision, status, sellerId, storeName, categoryId, categoryPath, name, description, thumbnailFileId(첫 이미지), minPrice, publishedAt, inStock, availabilityRevision.

## 색인
`catalog.product`와 `catalog.stock`을 소비 그룹 `search`로 받는다. 필드와 순서 규칙은 [context-map.md](context-map.md) "catalog.product", "catalog.stock"에 있다.
| 이벤트 | 반영 |
|---|---|
| `ProductRegistered`, `ProductPublished`, `ProductChanged` | 스냅샷 전체. `availabilityRevision`이 저장된 것보다 크면 `inStock`도 바꾼다 |
| `ProductStopped`, `ProductResumed`, `ProductBanned` | 상태만 바꾼다(`STOPPED`, `ON_SALE`, `BANNED`) |
| `StockAvailabilityChanged` | `inStock`만 바꾼다 |

- 상품 이벤트는 상품 `revision`이, 품절 전환은 `availabilityRevision`이 저장된 것보다 클 때만 반영한다. 두 토픽 사이 순서가 섞여도 같은 결과가 된다.
- 아직 모르는 상품의 `StockAvailabilityChanged`는 판매 가능 여부만 가진 문서를 만들어 둔다. 상태가 없으므로 검색에는 나오지 않는다.
- 문서를 읽고 고친 뒤 읽은 버전(`_seq_no`, `_primary_term`)이 그대로일 때만 쓴다. 두 토픽의 리스너가 같은 문서를 동시에 고치면 한쪽이 충돌하고 다시 읽어 반영한다.
- Elasticsearch 쓰기는 DB 트랜잭션에 묶이지 않는다. 쓰기 뒤 멱등 기록이 실패해 다시 처리해도 리비전 검사로 결과가 같다.
- 판매 시작이나 수정이 검색에 보이기까지는 Debezium 전달과 색인 새로 고침(1초)을 더해 몇 초다.

## 검색 API
gateway 경로는 `/api/search` + 아래 경로다. 토큰 없이 연다(gateway와 search의 `public-paths`).

`GET /v1/products?q=&categoryId=&minPrice=&maxPrice=&sort=&cursor=&size=`
| 파라미터 | 규칙 |
|---|---|
| `q` | 선택, 1~100자. 상품명(가중치 3), 상호, 설명에서 찾는다. 검색어의 모든 단어가 있어야 한다(`operator: and`, 필드를 가로질러 `cross_fields`) |
| `categoryId` | 선택. 대분류를 주면 그 아래 소분류 상품도 나온다(`categoryPath`) |
| `minPrice`, `maxPrice` | 선택, 0 이상. 상품 최저가(`minPrice`)로 거른다 |
| `sort` | `RELEVANCE`(기본), `LATEST`(판매 시작 최신), `PRICE_ASC`, `PRICE_DESC`. 검색어가 없으면 `RELEVANCE`는 `LATEST`와 같다 |
| `cursor`, `size` | 커서 페이징. `size` 1~100(기본 20). 응답의 `nextCursor`를 다음 요청에 넣는다. 마지막 쪽이면 `null` |

- 응답: `{items: [{productId, name, storeName, minPrice, inStock, thumbnailFileId, publishedAt}], totalCount, nextCursor}`. 이미지는 `/files/{thumbnailFileId}/w300`으로 받는다([file.md](file.md)).
- 판매 중(`ON_SALE`)인 상품만 나온다. 판매 중지·제재·등록 중 상품은 빠진다(SRC-3).
- 품절 상품(`inStock=false`)은 어떤 정렬에서도 판매 가능한 상품 뒤에 온다(SRC-3). 같은 값이면 productId 순이다.
- 커서는 마지막 결과의 정렬 값을 담은 불투명한 문자열이다(`search_after`). 형식이 틀리면 400 `SEARCH_INVALID_CURSOR`다.
- `totalCount`는 1만 건까지 정확하고, 넘으면 10000이다.
