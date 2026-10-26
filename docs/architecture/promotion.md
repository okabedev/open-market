# 쿠폰 (promotion 서비스)

쿠폰 정책, 발급, 주문 쿠폰의 예약·사용·복원을 갖는다(PRM-1~3). 할인 금액 계산도 이 서비스 한 곳에서 한다. order는 주문할 때 이 서비스에 할인을 조회해 스냅샷으로 저장하고([order.md](order.md) "쿠폰 적용"), 예약은 주문 Saga의 이벤트로 한다([messaging.md](messaging.md) "주문·결제 Saga").

M5 시작(2026-10-22)에 정한 규칙이다. API와 화면은 각 PR에서 이 문서에 더한다.

## 범위 결정(2026-10-22)
- 한 주문에 플랫폼 쿠폰(관리자가 만든다) 1장과 판매자별 판매자 쿠폰 1장까지 쓴다. 플랫폼 쿠폰은 플랫폼이, 판매자 쿠폰은 판매자가 부담한다. 판매자 쿠폰 부담분만 정산에서 뺀다([settlement.md](settlement.md) "정산 금액").
- 판매자 쿠폰은 그 판매자의 모든 상품에 쓴다. 상품이나 카테고리를 지정하는 쿠폰은 만들지 않는다.
- 쿠폰은 결제 전 취소(`OrderCancelled`)일 때만 복원한다. 결제한 뒤의 취소·반품은 쿠폰을 돌려주지 않고, 하위 주문에 나눈 할인을 뺀 결제 금액만 환불한다([fulfillment.md](fulfillment.md) "환불 금액"). PRM-3의 "주문 취소"는 결제 전 주문 취소다.
- 모든 쿠폰은 발급 수량이 있는 선착순 쿠폰이다. 수량 제한이 없는 쿠폰, 자동 발급, 쿠폰 코드 입력은 만들지 않는다.
- 쿠폰 정책은 만든 뒤 내용을 바꾸지 않는다. 발급 중지만 한다. 이미 발급한 쿠폰의 할인 조건이 바뀌지 않게 하기 위해서다.

## 쿠폰 정책(`Coupon`, PRM-1)
| 항목 | 규칙 |
|---|---|
| 발행 주체(`issuer`) | `PLATFORM`(관리자) 또는 `SELLER`(판매자 ID) |
| 이름 | 1~50자 |
| 할인 방식(`discountType`) | `FIXED`(정액, 원) 또는 `RATE`(정률, 1~100%) |
| 할인 값 | 정액은 1원 이상, 정률은 1~100 |
| 최대 할인 | 정률만. 선택, 1원 이상. 없으면 상한이 없다 |
| 최소 주문 금액 | 0원 이상. 아래 "할인 계산"의 적용 대상 금액과 비교한다 |
| 발급 수량 | 1~1,000,000 |
| 발급 기간 | 시작·끝 시각. 끝이 시작보다 뒤이고 만들 때 지금보다 뒤. 시작은 지나도 된다(바로 발급). 끝 시각부터 내려받을 수 없다 |
| 사용 기한 | 발급 후 일수(1~365). 발급 쿠폰의 만료 시각 = 발급 시각 + 일수 |

- 상태: `ISSUING`(발급 기간 안이면 내려받을 수 있다) → `STOPPED`(발급 중지, 끝 상태). 발급 기간이 지나도 상태는 `ISSUING`이고, 발급 가능 여부는 기간으로 정한다. 중지해도 이미 발급한 쿠폰은 쓸 수 있다.
- 관리자는 모든 쿠폰을, 판매자는 자기 쿠폰만 중지한다. 관리자 작업은 감사 로그를 남긴다(NFR-8): 플랫폼 쿠폰 생성 `COUPON_CREATED`, 중지 `COUPON_STOPPED`(대상 `COUPON`). 이미 중지한 쿠폰을 다시 중지하면 아무것도 바꾸지 않고 기록도 남기지 않는다.
- 정책에는 이벤트가 없다. 다른 서비스는 정책을 받지 않고, order는 할인 계산을 조회한다.

### API
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/v1/admin/coupons` | 플랫폼 쿠폰 생성. `Idempotency-Key` 필수. 201 `{couponId}` |
| POST | `/v1/admin/coupons/{couponId}/stop` | 쿠폰(플랫폼·판매자) 발급 중지. 204 |
| GET | `/v1/admin/coupons?issuer=&sellerId=&status=&page=&size=` | 관리자 목록. 쪽 번호, 최근 생성 순 |
| POST | `/v1/coupons/{couponId}/issue` | 구매자 쿠폰 내려받기. 201 또는 200 `{issuedCouponId}`(아래 "선착순 발급") |
| POST | `/v1/seller/coupons` | 판매자 쿠폰 생성. `Idempotency-Key` 필수. 201 `{couponId}` |
| POST | `/v1/seller/coupons/{couponId}/stop` | 자기 쿠폰 발급 중지. 204 |
| GET | `/v1/seller/coupons?status=&page=&size=` | 자기 쿠폰 목록. 쪽 번호, 최근 생성 순 |
| GET | `/v1/coupons?sellerId=` | 공개. 지금 내려받을 수 있는 쿠폰(`ISSUING`이고 발급 기간 안). `sellerId`가 없으면 플랫폼 쿠폰, 있으면 그 판매자 쿠폰. 발급 기간이 먼저 끝나는 순, 최대 100개(페이징 없음) |
| GET | `/v1/coupons/{couponId}` | 공개. 쿠폰 한 건. 상태와 상관없이 보인다 |
| GET | `/v1/issued-coupons?usable=&cursor=&size=` | 구매자 내 쿠폰. 최근 발급 순 커서 목록(아래 "내 쿠폰") |
| POST | `/v1/coupon-discounts` | 구매자 주문서 할인 미리보기(아래 "할인 미리보기") |

- 생성 본문: `name`, `discountType`(`FIXED`/`RATE`), `discountValue`, `maxDiscount`(정률만, 선택), `minOrderAmount`, `quantity`, `issueStartAt`, `issueEndAt`, `validDays`. 위 표의 규칙을 어기면 400 `PROMOTION_INVALID_INPUT`이다.
- 없는 쿠폰, 판매자에게 다른 판매자의 쿠폰이나 플랫폼 쿠폰은 404 `PROMOTION_COUPON_NOT_FOUND`다.
- 중지는 쿠폰 행을 잠그고 한다.
- 조회 응답의 `issuedCount`는 DB 발급 수다. 남은 수량은 `quantity - issuedCount`다.

## 발급 쿠폰(`IssuedCoupon`, PRM-2)
| 상태 | 뜻 | 다음 |
|---|---|---|
| `AVAILABLE` | 쓸 수 있다(만료 시각 전) | `RESERVED` |
| `RESERVED` | 결제 전 주문에 묶였다 | `USED`(결제 완료), `AVAILABLE`(결제 전 취소) |
| `USED` | 결제된 주문에 쓰였다. 끝 상태 | |

- 만료는 상태가 아니다. 만료 시각이 지난 `AVAILABLE`은 쓸 수 없고 목록에 "만료"로 보인다. 결제 전 취소로 복원했을 때 만료 시각이 지났으면 그대로 만료다(PRM-3 "유효기간이 남은 쿠폰은 복원").
- 쿠폰 정책과 회원의 쌍은 하나다(유니크 `(coupon_id, member_id)`). 복원해도 새 쿠폰을 만들지 않는다.

### 내 쿠폰
`GET /v1/issued-coupons?usable=&cursor=&size=`(shop 내 쿠폰, 주문서 쿠폰 고르기)
- 로그인한 회원의 발급 쿠폰을 최근 발급 순(발급 시각, ID)으로 준다. 커서 목록이고(`size` 1~100, 기본 20) 틀린 커서는 400 `PROMOTION_INVALID_CURSOR`다. 인덱스 `issued_coupon_owner_idx`(member_id, issued_at)를 쓴다.
- 항목: `{issuedCouponId, couponId, issuer, sellerId, name, discountType, discountValue, maxDiscount, minOrderAmount, state, issuedAt, expiresAt}`. 정책은 쿠폰 행과 이어 읽는다(N+1 없음).
- `state`는 화면 상태다: `USABLE`(`AVAILABLE`이고 만료 시각 전), `RESERVED`, `USED`, `EXPIRED`(`AVAILABLE`이지만 만료 시각이 지남). 만료는 도메인 상태가 아니라 조회할 때 정한다.
- `usable=true`면 `USABLE`만 준다. 주문서는 이것을 받아 주문의 판매자와 최소 주문 금액으로 고를 수 있는 쿠폰을 거르고, 고른 뒤 아래 미리보기로 금액을 확인한다.
- 판매자 쿠폰에는 상호가 없다(promotion은 상호를 갖지 않는다). 화면은 장바구니·주문서의 판매자로 이름을 붙인다.

### 할인 미리보기
`POST /v1/coupon-discounts`(shop 주문서)
- 요청: `{issuedCouponIds, sellerOrders: [{sellerId, amount}]}`. 구매자는 토큰의 `sub`다. 응답과 검증(400 `PROMOTION_INVALID_INPUT`)은 내부 API와 같고, 같은 유스케이스(`CalculateCouponDiscountUseCase`)로 계산한다(아래 "할인 계산 API(내부)").
- 주문서는 결과의 `paymentAmount`를 주문 생성의 `expectedAmount`로 보낸다. 미리보기와 주문 사이에 쿠폰을 다른 주문에 쓰면 주문 생성이 422 `ORDER_COUPON_NOT_APPLICABLE`이다.
- 쿠폰을 묶지 않는 조회라 `Idempotency-Key`를 받지 않는다.

### 화면(shop)
- 쿠폰 받기 `/coupons`(플랫폼 쿠폰)와 상품 상세(그 판매자 쿠폰)가 `GET /v1/coupons`를 보이고 "받기"는 Server Action이 발급을 부른다. 결과는 `?notice=`(받음, 소진, 발급 중 아님, 한도 초과, 실패)로 보인다. 로그인했으면 이미 받은 쿠폰은 "받음"이다(내 쿠폰의 couponId).
- 내 쿠폰 `/my/coupons`는 `state`를 "사용 가능", "주문에 사용 중", "사용함", "기간 만료"로 보인다.
- 주문서는 `usable=true`로 받은 쿠폰 중 플랫폼 쿠폰과 주문의 판매자 쿠폰을 고르게 한다(판매자마다 한 장, 플랫폼 한 장, GET 폼 `?sellerCoupon=&platformCoupon=`). 고르면 할인 미리보기로 할인과 결제 금액을 보이고, 쓸 수 없는 쿠폰은 이유를 보이고 쿠폰 없이 계산한다. 주문할 때 고른 쿠폰과 그 결제 금액(`expectedAmount`)을 보낸다.

### 화면(seller)
- 쿠폰 `/coupons`: 내 쿠폰을 최근 생성 순, 쪽 번호로 보인다. 탭은 전체·발급 중(`ISSUING`)·발급 중지(`STOPPED`)다. 단계는 상태와 발급 기간으로 "발급 예정", "발급 중", "발급 끝남", "발급 중지"로 보인다(`couponPhase`, `packages/ui`). 발급 수 / 수량을 보이고, 중지하지 않은 쿠폰은 확인한 뒤 발급을 멈춘다.
- 쿠폰 만들기 `/coupons/new`: 위 "쿠폰 정책" 규칙을 Server Action이 먼저 검사하고(`lib/coupon-form.ts`), 화면을 그릴 때 만든 `Idempotency-Key`로 만든다. 발급 시각은 한국 시간 `datetime-local`로 받아 ISO로 바꾼다. 기본 발급 기간은 다음 정시부터 7일, 사용 기한은 30일이다. 최대 할인 칸은 정률일 때만 보이고 정액이면 보내지 않는다.

### 선착순 발급
구매자가 `POST /v1/coupons/{couponId}/issue`로 내려받는다. 새로 받으면 201, 이미 받았으면 200이고 둘 다 `{issuedCouponId}`다(다시 눌러도 같은 결과).
1. 쿠폰이 `ISSUING`이고 발급 기간 안이어야 한다. 아니면 409 `PROMOTION_COUPON_NOT_ISSUING`이다. 없는 쿠폰은 404 `PROMOTION_COUPON_NOT_FOUND`다.
2. Redis Lua 스크립트 하나로 1인 1매와 수량을 함께 확인하고 차지한다. 키는 발급 수 `promotion:coupon-stock:<couponId>`와 발급한 회원 집합 `promotion:coupon-issued:<couponId>`이고, 발급 기간 끝 + 1시간에 사라진다.
   - 이미 집합에 있으면 차지하지 않는다. DB의 발급 쿠폰을 찾아 200으로 준다. DB에 없으면(차지한 요청이 저장 전에 끝남) 3으로 간다.
   - 발급 수가 수량 이상이면 409 `PROMOTION_COUPON_SOLD_OUT`이다. DB에 가지 않는다.
   - 아니면 집합에 넣고 발급 수를 1 올린다.
3. DB 트랜잭션 하나에서 저장한다. 이미 발급 행이 있으면 그것을 준다. 없으면 쿠폰 행의 발급 수(`issued_count`)를 조건부 UPDATE로 1 올리고(발급 중, 발급 기간 안, 발급 수 < 수량일 때만) 발급 쿠폰을 넣는다(`ON CONFLICT (coupon_id, member_id) DO NOTHING`). 올리지 못하면 중지면 409 `PROMOTION_COUPON_NOT_ISSUING`, 아니면 409 `PROMOTION_COUPON_SOLD_OUT`이다.
   - 저장이 실패하면 Redis에서 차지한 것을 되돌린다(집합에서 빼고 발급 수를 1 내린다). DB가 매진이라고 하면 Redis 발급 수도 수량으로 올려 다음 요청부터 Redis에서 거른다.
   - 같은 회원의 요청이 동시에 저장하면 늦은 쪽은 0행이 되고 트랜잭션을 롤백해 올린 발급 수를 되돌린다. 먼저 저장한 발급 쿠폰을 200으로 준다.

#### 정합성(R11, 2026-10-23 확정)
- 수량과 1인 1매는 DB가 마지막으로 지킨다. 수량은 쿠폰 행의 조건부 UPDATE와 `CHECK (issued_count BETWEEN 0 AND quantity)`, 1인 1매는 유니크 `(coupon_id, member_id)`다. Redis는 DB까지 가는 요청을 수량 근처로 줄이는 앞단 필터다. 그래서 Redis가 DB와 어긋나도 초과 발급이나 1인 2매가 되지 않는다.
- Redis 키가 없으면(재시작, 처음 발급) DB의 발급 회원으로 집합을 채우고, 발급 수(= 회원 수)를 두는 것과 차지를 스크립트 하나로 한다. 그 사이 키가 다시 사라져도 차지가 키 없이 끝나지 않는다. 키를 다시 만드는 동안 끼어든 요청을 따로 막지 않는다. 끼어들어 Redis가 적게 세면 DB가 막는다.
- 어긋남이 남는 쪽: 차지한 요청이 되돌리기 전에 죽거나 되돌리기가 실패하면 Redis가 많게 센다. 그 쿠폰은 키가 사라질 때까지 수량보다 적게 나갈 수 있다(초과 발급의 반대쪽이라 받아들인다).
- 확인: 수량 30에 회원 120명이 두 번씩 동시에 누르고 그사이 Redis 키를 20번 지워도 DB 발급 수 30, 회원당 1장, 같은 회원의 두 요청이 같은 발급 쿠폰을 받는다(`IssueCouponApiIntegrationTest`). 저장 실패, Redis 장애, Redis가 적게 센 경우, 집합에서 빠진 경우는 유스케이스 테스트(`IssueCouponUseCaseTest`)가 본다.

- Redis를 부를 수 없으면 503 `PROMOTION_UNAVAILABLE`이다. DB만으로 발급하지 않는다.
- 발급은 상태가 정해지는 요청이라 `Idempotency-Key`는 받지 않는다(위 2의 "이미 발급").
- gateway는 이 경로에 엄격한 한도(사용자마다 1분에 `openmarket.gateway.strict-rate-limit.requests-per-minute`회)를 더 건다.

## 할인 계산
order의 주문 생성과 shop 주문서가 같은 계산을 쓴다. promotion이 계산하고, order는 결과를 스냅샷으로 저장한다.
1. 판매자 쿠폰: 하위 주문마다 그 판매자의 쿠폰 하나. 적용 대상 금액은 그 하위 주문의 상품 금액(항목 금액의 합)이다.
2. 플랫폼 쿠폰: 주문에 하나. 적용 대상 금액은 판매자 쿠폰 할인을 뺀 뒤의 주문 상품 금액이다.
3. 쿠폰마다 적용 대상 금액이 최소 주문 금액보다 작으면 쓸 수 없다. 할인은 정액이면 할인 값, 정률이면 `floor(대상 금액 × 율 / 100)`을 최대 할인으로 자른 값이다. 할인은 대상 금액을 넘지 않는다.
4. 플랫폼 쿠폰 할인은 하위 주문에 나눈다. 각 하위 주문의 몫은 (판매자 쿠폰을 뺀 하위 주문 금액) 비율로 내림하고, 남은 원은 내림으로 버린 값이 큰 하위 주문부터 1원씩 더한다(같으면 하위 주문 순서). 몫이 하위 주문 금액을 넘지 않는다.
- 결과는 하위 주문마다 판매자 쿠폰 할인, 플랫폼 쿠폰 할인 몫, 결제 금액(상품 금액 − 두 할인)이다. 주문 결제 금액은 하위 주문 결제 금액의 합이다.
- 쓸 수 없는 쿠폰이 있으면 계산은 쿠폰마다 이유를 준다: 내 쿠폰이 아님(`NOT_OWNED`, 없는 발급 쿠폰 포함), 이미 묶임·사용됨(`NOT_AVAILABLE`), 만료(`EXPIRED`, 만료 시각부터), 최소 주문 금액 미달(`MIN_ORDER_AMOUNT_NOT_MET`), 다른 판매자 쿠폰(`SELLER_NOT_IN_ORDER`, 주문에 그 판매자 상품이 없음), 같은 자리에 두 장(`DUPLICATE_SLOT`, 플랫폼 둘, 같은 판매자 둘, 같은 쿠폰 두 번. 그 자리의 쿠폰 모두).
- 계산은 도메인 규칙(`DiscountCalculator`)이라 커맨드 쪽 유스케이스(`CalculateCouponDiscountUseCase`, 읽기 전용 트랜잭션)가 한다. 쿠폰을 묶지 않는다.

### 할인 계산 API(내부)
order가 주문을 만들 때 부른다(OpenFeign, `internal` scope 토큰). 상태를 바꾸지 않는 조회지만 본문이 있어 POST다.

`POST /internal/v1/coupon-discounts`
- 요청: `{buyerId, issuedCouponIds, sellerOrders: [{sellerId, amount}]}`. amount는 하위 주문 상품 금액(원)이다. 하위 주문이 없거나 판매자가 겹치거나 금액이 음수이거나 쿠폰이 50장을 넘으면 400 `PROMOTION_INVALID_INPUT`이다.
- 응답(200): `{applicable, sellerOrders: [{sellerId, amount, sellerIssuedCouponId, sellerCouponDiscount, platformCouponDiscount, paymentAmount}], platformIssuedCouponId, platformCouponDiscount, paymentAmount, unavailable: [{issuedCouponId, reason}]}`. sellerOrders는 요청 순서다. 쓸 수 없는 쿠폰이 있어도 200이고 `applicable=false`다(order가 422 `ORDER_COUPON_NOT_APPLICABLE`로 바꾼다).

## 주문 쿠폰 예약·사용·복원
`order.order`를 소비 그룹 `promotion`으로 받는다. 결과는 `promotion.issued-coupon` 토픽에 키 orderId로 낸다(한 주문의 결과가 순서대로 가게).
| 받는 이벤트 | 처리 | 내는 이벤트 |
|---|---|---|
| `OrderPlaced`(issuedCouponIds가 있을 때) | 쿠폰을 모두 잠그고, 모두 구매자 것이고 `AVAILABLE`이고 만료 전이면 `RESERVED`(주문 ID 기록). 하나라도 아니면 아무것도 묶지 않는다 | `CouponReserved` 또는 `CouponReservationFailed`(reason `COUPON_UNAVAILABLE`) |
| `OrderPaid` | 그 주문에 묶인 쿠폰을 `USED` | 없음 |
| `OrderCancelled` | 그 주문에 묶인 쿠폰을 `AVAILABLE`로 되돌리고 주문 ID를 지운다. 묶인 쿠폰이 없으면(예약 실패, 쿠폰 없는 주문) 아무것도 하지 않는다 | 없음 |

- issuedCouponIds가 빈 `OrderPlaced`는 받고 넘긴다. order는 쿠폰이 없으면 쿠폰 결과를 기다리지 않는다.
- 할인 금액은 다시 계산하지 않는다. 정책을 바꾸지 않으므로 주문할 때 조회한 할인이 그대로다. 그사이 쿠폰이 다른 주문에 묶였거나 만료됐는지만 본다.
- 리스너는 처리 기록으로 같은 이벤트를 한 번만 처리하고, 발급 쿠폰 행을 잠그고 바꾼다. 리스너 이름은 `promotion.<이벤트>`다(예: `promotion.order-placed`).
- 이미 같은 주문에 묶였거나 쓰였으면 넘긴다(재처리). 요청한 쿠폰이 모두 이 주문에 묶였으면 이벤트도 다시 내지 않는다. 한 주문의 이벤트는 순서대로 오므로 `OrderCancelled`가 `OrderPlaced`보다 먼저 오지 않는다.
- 예약은 발급 쿠폰 행을 ID 순서로 잠근다. 같은 쿠폰을 쓰는 두 주문이 동시에 와도 먼저 잠근 주문만 묶고, 늦은 주문은 `CouponReservationFailed`다.
- `CouponReservationFailed`의 issuedCouponIds는 쓸 수 없는 쿠폰(요청 순서)이다.
