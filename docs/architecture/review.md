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

## 리뷰(`Review`, REV-1)
| 항목 | 규칙 |
|---|---|
| 대상 | 내 리뷰 작성 자격의 주문 항목. 남의 것이거나 없으면 404 `REVIEW_NOT_ELIGIBLE` |
| 별점 | 정수 1~5 |
| 본문 | 10~1,000자. 앞뒤 공백은 지운다 |
| 사진 | 리뷰 사진(`REVIEW_IMAGE`, 공개) 0~5장, 중복 없이. file이 `ReviewCreated`를 받아 붙인다([file.md](file.md)) |

- 주문 항목마다 하나다(유니크). 이미 썼으면 409 `REVIEW_ALREADY_WRITTEN`이다. `Idempotency-Key`가 필요하다.
- 리뷰를 저장하고, 같은 트랜잭션에서 상품 별점 요약을 바꾸고 `ReviewCreated`와 `RatingSummaryChanged`를 낸다.
- 상태: `VISIBLE` ↔ `HIDDEN`. 숨긴 리뷰는 상품 리뷰 목록과 별점 요약에서 빠진다. 작성자에게는 "숨김"으로 보인다.

## 답글(`ReviewReply`, REV-3)
- 리뷰 상품의 판매자만 단다. 1~1,000자. 리뷰마다 하나이고, 다시 쓰면 고친다(204). 다른 판매자의 리뷰면 404 `REVIEW_NOT_FOUND`다.
- 숨긴 리뷰에도 답글을 쓸 수 있지만 공개 목록에는 리뷰와 함께 빠진다.

## 숨김(REV-3, ADM-2)
- 관리자가 사유(1~200자)를 남기고 숨기거나 다시 보인다. 같은 상태로 다시 하면 204, 이벤트 없음이다. 감사 로그(`REVIEW_HIDDEN`, `REVIEW_SHOWN`)를 남긴다(NFR-8).
- 상태가 바뀌면 같은 트랜잭션에서 별점 요약을 다시 계산하고 `RatingSummaryChanged`를 낸다.

## 별점 요약(`RatingSummary`, REV-2)
- 상품마다 보이는 리뷰 수와 별점 합, 리비전을 둔다. 평균은 합 ÷ 수를 소수 첫째 자리에서 반올림해 보여 준다(리뷰가 없으면 평균 없음).
- 리뷰 작성과 숨김·보임은 요약 행을 잠그고 바꾼다(`SELECT ... FOR UPDATE`, 없으면 먼저 만든다). 바꿀 때마다 리비전을 1 올린다.
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
