# 리뷰 (review 서비스)

구매확정한 주문 항목의 리뷰, 판매자 답글, 관리자 숨김, 상품별 별점 요약을 갖는다(REV-1~3). 별점 요약은 이벤트로 search에 보내 검색 결과에 보이고(REV-2), 상품 상세는 BFF가 이 서비스의 조회 API를 함께 부른다.

M5 시작(2026-10-22)에 정한 규칙이다. API와 화면은 각 PR에서 이 문서에 더한다.

## 범위 결정(2026-10-22)
- 리뷰는 쓴 뒤 고치거나 지우지 않는다. 한 번 쓰면 끝이다(REV-1 "한 번 쓴다"). 부적절한 리뷰는 관리자가 숨긴다.
- 작성 기한은 두지 않는다. 구매확정한 주문 항목이면 언제든 쓴다.
- 답글은 리뷰마다 하나이고 판매자가 고칠 수 있다. 답글 알림은 보내지 않는다.
- 상품 상세의 별점과 리뷰 목록은 BFF가 catalog 상세와 review 조회를 함께 불러 합친다. catalog는 별점을 저장하지 않는다.

## 리뷰 작성 자격(`ReviewEligibility`)
`fulfillment.shipment`의 `PurchaseConfirmed`를 소비 그룹 `review`로 받아 주문 항목마다 하나 만든다. 주문 항목 ID, 하위 주문 ID, 상품, 상품명, 옵션, 판매자, 구매자, 구매확정 시각을 고정한다.
- 주문 항목 ID 유니크로 한 번만 생긴다. 같은 이벤트를 다시 받으면 넘긴다.
- 리뷰를 쓰면 자격에 리뷰 ID를 남긴다. 구매자의 "쓸 수 있는 리뷰" 목록은 리뷰 ID가 없는 자격이다.
- 리스너 이름은 `review.purchase-confirmed`다. 다른 이벤트 ID로 같은 구매확정이 다시 와도 주문 항목 ID(기본 키)로 하나만 생긴다.

## 리뷰(`Review`, REV-1)
| 항목 | 규칙 |
|---|---|
| 대상 | 내 리뷰 작성 자격의 주문 항목. 남의 것이거나 없으면 404 `REVIEW_NOT_ELIGIBLE` |
| 별점 | 정수 1~5 |
| 본문 | 10~1,000자. 앞뒤 공백은 지운다 |
| 사진 | 리뷰 사진(`REVIEW_IMAGE`, 공개) 0~5장, 중복 없이. file이 `ReviewCreated`를 받아 붙인다([file.md](file.md)) |

- 주문 항목마다 하나다(유니크). 이미 썼으면 409 `REVIEW_ALREADY_WRITTEN`이다. `Idempotency-Key`가 필요하다.
- 리뷰를 저장하고, 같은 트랜잭션에서 상품 별점 요약을 바꾸고 `ReviewCreated`와 `RatingSummaryChanged`를 낸다.
- 작성 자격 행을 잠그고(`SELECT ... FOR UPDATE`) 쓴다. 같은 주문 항목의 작성이 동시에 오면 뒤 요청은 앞 요청이 끝난 뒤 409다. 잠그는 순서는 늘 작성 자격 → 별점 요약이다.
- 입력 규칙을 어기면 400 `REVIEW_INVALID_INPUT`이다. 이때 작성 자격은 그대로다.
- 상태: `VISIBLE` ↔ `HIDDEN`. 숨긴 리뷰는 상품 리뷰 목록과 별점 요약에서 빠진다. 작성자에게는 "숨김"으로 보인다.

### API
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/v1/reviews` | 리뷰 작성. 로그인한 회원(작성 자격의 구매자). `Idempotency-Key` 필수. 201 `{reviewId}` |
| GET | `/v1/products/{productId}/rating-summary` | 공개. 상품 별점 요약 `{productId, ratingCount, ratingAverage}`. 리뷰가 없거나 없는 상품이면 `ratingCount` 0, `ratingAverage` null(상품이 있는지는 보지 않는다) |
| GET | `/v1/products/{productId}/reviews` | 공개. 상품의 보이는 리뷰. 커서 목록 |
| GET | `/v1/reviews` | 내가 쓴 리뷰. 숨긴 리뷰도 `status`와 함께 보인다. 커서 목록 |
| GET | `/v1/reviews/writable` | 리뷰를 쓸 수 있는 내 구매확정 주문 항목(리뷰 ID가 없는 작성 자격). 구매확정 최근 순 커서 목록 |
| GET | `/v1/seller/reviews` | 판매자. 자기 상품의 리뷰(숨긴 리뷰 포함). 커서 목록 |
| PUT | `/v1/seller/reviews/{reviewId}/reply` | 판매자 답글 달기·고치기 `{content}`. 204 |
| GET | `/v1/admin/reviews` | 관리자. 모든 리뷰. `status`, `productId`, `sellerId`로 거르는 쪽 번호 목록(`page`, `size` 최대 100) |
| POST | `/v1/admin/reviews/{reviewId}/hide`, `/show` | 관리자 숨김·다시 보임 `{reason}`. 204 |

- 작성 본문: `orderLineId`, `rating`, `content`, `fileIds`(선택, 없으면 사진 없음). `fileIds`는 회원 업로드 `POST /api/file/v1/files`(`purpose=REVIEW_IMAGE`)로 받은 파일이다([file.md](file.md) "리뷰 사진").
- 목록은 작성 최근 순이고 시각이 같으면 리뷰 ID 역순이다. 커서 목록은 `?cursor=&size=`(기본 20, 최대 50)이고 `{items, totalCount, nextCursor}`다. 틀린 커서는 400 `REVIEW_INVALID_CURSOR`다([conventions.md](conventions.md) "REST API").
- 공개 상품 리뷰는 작성자를 싣지 않는다(`reviewId`, `optionValues`, `rating`, `content`, `fileIds`, `reply`, `createdAt`). 내·판매자·관리자 목록은 주문 항목, 상품, 판매자, 구매자 ID와 `status`를 더 싣는다. 리뷰 서비스는 구매자 이름을 갖지 않는다. shop 상품 상세도 작성자를 보이지 않고 별점, 고른 옵션, 날짜, 본문, 사진, 판매자 답글만 보인다(2026-10-26 결정).
- 사진은 `fileIds`만 준다. 화면이 `/files/{fileId}/w300`, `/files/{fileId}/w800`으로 받는다(catalog 상품 이미지와 같다). `reply`는 `{content, createdAt, updatedAt}`이고 없으면 null이다.
- 답글은 목록 쿼리에서 함께 읽고, 사진은 그 쪽의 리뷰 ID로 한 번 더 읽는다(쪽마다 쿼리 2개 + 개수 1개).
- 상품 리뷰 목록은 부분 인덱스 `review_product_idx`(`WHERE status = 'VISIBLE'`)를 쓴다. 상태를 바인딩 값으로 비교하면 일반 실행 계획에서 부분 인덱스를 못 쓰고 `review_created_idx`로 전체를 훑으므로(`EXPLAIN`으로 확인) SQL 상수로 비교한다.

### 화면(shop)
- 내 리뷰 `/my/reviews`: 위는 "쓸 수 있는 리뷰"(`/v1/reviews/writable`), 아래는 "내가 쓴 리뷰"(`/v1/reviews`)다. 목록마다 커서(`?writableCursor=`, `?cursor=`)를 따로 둔다. 숨긴 리뷰는 "숨김"과 함께 보인다. 구매확정한 하위 주문 상세에서도 이 화면으로 간다.
- 리뷰 쓰기 `/orders/{orderId}/reviews/{orderLineId}`: 상품 이름과 옵션은 주문 상세(order)에서 읽는다. 별점(1~5점 라디오), 본문, 사진을 받고 화면을 그릴 때 만든 `Idempotency-Key`로 쓴다. 쓰면 `/my/reviews?notice=review-written`로 간다. 409·404는 review 문구를 폼에 보인다.
- 사진은 고르는 즉시 BFF `/api/files?purpose=REVIEW_IMAGE`로 한 장씩 올리고, 공개 경로 `/files/{fileId}/w300`으로 미리 본다(붙기 전 `TEMP`도 공개 용도면 보인다, [file.md](file.md) "썸네일과 제공").

## 답글(`ReviewReply`, REV-3)
- 리뷰 상품의 판매자만 단다. 1~1,000자. 리뷰마다 하나이고, 다시 쓰면 고친다(204). 다른 판매자의 리뷰면 404 `REVIEW_NOT_FOUND`다.
- 숨긴 리뷰에도 답글을 쓸 수 있지만 공개 목록에는 리뷰와 함께 빠진다.
- 리뷰 행을 잠그고 쓴다. 같은 리뷰의 첫 답글이 동시에 와도 하나만 생긴다. 같은 본문으로 다시 쓰면 바꾸지 않는다(고친 시각 그대로). 이벤트는 내지 않는다.

## 숨김(REV-3, ADM-2)
- 관리자가 사유(1~200자)를 남기고 숨기거나 다시 보인다. 같은 상태로 다시 하면 204, 이벤트 없음이다. 감사 로그(`REVIEW_HIDDEN`, `REVIEW_SHOWN`)를 남긴다(NFR-8).
- 상태가 바뀌면 같은 트랜잭션에서 별점 요약을 다시 계산하고 `RatingSummaryChanged`를 낸다.
- 리뷰 행 → 별점 요약 행 순으로 잠근다. 같은 리뷰를 동시에 여러 번 숨겨도 요약에서 한 번만 빠지고 이벤트도 하나다(테스트로 확인). 사유가 틀리면 400 `REVIEW_INVALID_INPUT`, 없는 리뷰는 404 `REVIEW_NOT_FOUND`다.
- 감사 로그 대상은 `REVIEW`이고 사유를 `detail`에 남긴다. 리뷰에는 사유를 저장하지 않는다.

## 별점 요약(`RatingSummary`, REV-2)
- 상품마다 보이는 리뷰 수와 별점 합, 리비전을 둔다. 평균은 합 ÷ 수를 소수 첫째 자리로 반올림(`HALF_UP`)해 보여 준다. 예: 13 ÷ 3 → 4.3, 81 ÷ 20 → 4.1. 리뷰가 없으면 평균 없음이다. 이벤트와 조회 API가 같은 규칙으로 계산한다(테스트가 같은지 확인한다).
- 리뷰 작성과 숨김·보임은 요약 행을 잠그고 바꾼다(`SELECT ... FOR UPDATE`, 없으면 먼저 만든다). 바꿀 때마다 리비전을 1 올린다.
  - 요약 행이 없으면 빈 요약(리비전 0)을 `INSERT ... ON CONFLICT DO NOTHING`으로 넣고 잠근다. 같은 상품의 첫 리뷰가 동시에 와도 유니크 충돌 없이 줄을 선다. 같은 상품에 8건을 동시에 써서 수·합이 맞고 리비전 1~8이 한 번씩 나가는 것을 테스트로 확인했다.
- `RatingSummaryChanged`는 바뀐 뒤의 값(리뷰 수, 별점 합, 평균, 리비전)을 싣는다. search는 리비전이 저장된 것보다 클 때만 반영한다. 다시 받거나 순서가 섞여도 같은 결과다.

## 이벤트
필드는 [context-map.md](context-map.md) "review.review, review.rating-summary".
| 받는 이벤트 | 처리 |
|---|---|
| `PurchaseConfirmed`(fulfillment.shipment) | 주문 항목마다 리뷰 작성 자격. 같은 토픽의 다른 이벤트는 `type` 헤더로 넘긴다 |

| 내는 이벤트 | 토픽 | 언제 |
|---|---|---|
| `ReviewCreated` | review.review(키 reviewId) | 작성 |
| `RatingSummaryChanged` | review.rating-summary(키 productId) | 작성, 숨김, 다시 보임 |

- 리스너는 처리 기록으로 같은 이벤트를 한 번만 처리한다. 리스너 이름은 `review.<이벤트>`다(예: `review.purchase-confirmed`).
