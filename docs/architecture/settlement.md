# 정산 (settlement 서비스)

구매확정된 하위 주문을 정산 대상으로 모으고, 주 단위 배치로 판매자별 정산을 확정하고, 관리자의 지급 처리를 기록한다(STL-1~4). 카테고리별 수수료율도 이 서비스가 갖는다([catalog.md](catalog.md)의 카테고리 ID를 쓴다).

M5 시작(2026-10-22)에 정한 규칙이다. API와 화면은 각 PR에서 이 문서에 더한다.

## 범위 결정(2026-10-22)
- 배치는 다른 시간 기반 작업처럼 `@Scheduled` + ShedLock으로 만든다. Spring Batch는 쓰지 않는다. 멱등은 판매자·정산 기간 유니크 키로 지킨다.
- 실제 송금은 하지 않는다(STL-3). 지급 처리는 지급 완료 기록이다. 정산 계좌는 member에 있고, 관리자 화면이 BFF로 함께 보인다.
- 구매확정한 뒤에는 클레임이 없어서 정산 대상은 바뀌지 않는다. 확정한 정산을 되돌리거나 다시 계산하지 않는다.
- 정산 금액이 0 이하(판매자 쿠폰이 큰 경우)인 정산도 확정한다. 지급할 금액은 0원이고, 판매자에게 받을 돈을 다음 정산과 상계하는 일은 하지 않는다.
- 판매자 탈퇴(폐점)는 M5에서도 막는다([member.md](member.md) "탈퇴"). PRD에 폐점 요구사항이 없다.

## 수수료율(`CommissionRate`)
- 소분류 카테고리마다 요율을 bp(0.01%) 단위 정수 0~10,000으로 둔다. 요율이 없는 카테고리는 기본 요율 `openmarket.settlement.default-commission-rate`(기본 1,000bp = 10%)다.
- 관리자가 카테고리별로 정하거나 지운다. 감사 로그(`COMMISSION_RATE_CHANGED`)를 남긴다. 바꾼 요율은 그 뒤에 생기는 정산 대상부터 쓴다.
- 감사 로그 대상은 `CATEGORY`(카테고리 ID)이고 `detail`에 이전 → 새 요율을 남긴다(예: `기본(1000bp) → 850bp`, `850bp → 기본(1000bp)`). 같은 요율로 정하거나 없는 요율을 지우면 204이고 감사 로그가 없다.
- 요율 행을 잠그고 바꾼다. 카테고리 ID가 catalog에 있는지는 보지 않는다. 관리자 화면이 catalog 카테고리 트리에서 고르고, 없는 카테고리의 요율은 쓰이지 않을 뿐이다.
- 기본 요율 설정도 0~10,000bp여야 한다. 아니면 서비스가 뜨지 않는다.

### API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/admin/commission-rates` | 관리자. `{defaultRateBp, rates: [{categoryId, rateBp, updatedAt}]}`. 요율을 정한 카테고리만 카테고리 ID 순으로 준다. 카테고리 이름은 화면이 catalog 트리로 붙인다 |
| PUT | `/v1/admin/commission-rates/{categoryId}` | 관리자. `{rateBp}`(필수, 0~10,000). 204. 범위 밖이면 400 `SETTLEMENT_INVALID_INPUT` |
| DELETE | `/v1/admin/commission-rates/{categoryId}` | 관리자. 요율을 지워 기본 요율로 돌린다. 204 |

## 정산 대상(`SettlementItem`, STL-1)
`fulfillment.shipment`의 `PurchaseConfirmed`를 소비 그룹 `settlement`로 받아 하위 주문마다 하나 만든다.
| 금액 | 계산 |
|---|---|
| 판매 금액 | 항목 금액(개당 가격 × 수량)의 합. 쿠폰을 빼기 전이다 |
| 수수료 | 항목마다 `floor(항목 금액 × 그 카테고리 요율 / 10,000)`의 합. 받은 때의 요율을 항목에 고정한다 |
| 판매자 쿠폰 부담분 | 이벤트의 판매자 쿠폰 할인. 플랫폼 쿠폰 할인은 플랫폼이 부담해 빼지 않는다 |
| 정산 금액 | 판매 금액 − 수수료 − 판매자 쿠폰 부담분 |

- 하위 주문 ID 유니크로 한 번만 생긴다.
- 항목마다 주문 항목, 상품, 상품명, 카테고리, 수량, 항목 금액, 받은 때의 요율(bp)과 수수료를 함께 저장한다(`settlement_item_line`). 판매자 정산 내역에 쓴다.
- 리스너 이름은 `settlement.purchase-confirmed`다. 다른 이벤트 ID로 같은 구매확정이 다시 와도 하위 주문 ID로 하나만 생긴다.
- 정산 기간은 구매확정 시각으로 정한다(아래 "정산 기간"). 받았을 때 그 판매자의 그 기간이 이미 확정됐으면 다음 기간에 넣는다. 늦게 온 이벤트가 확정된 정산을 바꾸지 않게 하기 위해서다.
- 정산 대상 만들기와 확정은 판매자 단위로 잠그고 한다. 둘이 겹쳐 확정된 기간에 대상이 들어가지 않게 한다.
  - 방법(2026-10-25): Postgres 트랜잭션 advisory lock `pg_advisory_xact_lock(키)`. 키는 판매자 ID(UUID)의 두 64비트 절반을 XOR한 값이고, 트랜잭션이 끝나면 풀린다. 잠금용 행이나 테이블이 필요 없다. 다른 판매자와 키가 겹치면 둘이 서로 기다릴 뿐이다. settlement 스키마에서 advisory lock을 쓰는 곳은 이것뿐이다.
  - 정산 대상 만들기: 판매자를 잠그고 → 하위 주문이 이미 있으면 넘기고 → 구매확정 시각의 기간이 그 판매자에게 이미 확정됐으면 확정되지 않은 다음 기간까지 넘긴다.
  - 같은 판매자의 대상 만들기 8건과 정산 작업 2번을 동시에 돌려, 정산마다 금액·대상 수가 그 정산의 대상 합과 같고 판매자·기간마다 정산이 하나인 것을 테스트로 확인했다. 잠금을 빼면 같은 테스트가 판매자·기간 유니크 위반으로 실패한다.

## 정산 기간
- 기간은 기준 시각 `openmarket.settlement.period.anchor`(기본 `2026-01-05T00:00+09:00`, 월요일)에서 길이 `openmarket.settlement.period.length`(기본 7일)씩 나눈 구간 `[시작, 끝)`이다. 기본값이면 월요일 0시(한국 시간)부터 다음 월요일 0시까지다.
- E2E는 환경 변수로 길이를 줄인다(예: 1분). 결제 기한, 자동 구매확정과 같은 방식이다([dev-setup.md](../dev-setup.md) "E2E").
  - 환경 변수는 `SETTLEMENT_PERIOD_LENGTH`(config-repo `settlement.yml` 기본 `7d`)이고 `apps.yaml`은 값이 있을 때만 넘긴다. `run.sh`에서 줄이는 값은 M5 완료 E2E PR에서 정한다.
- 기간 경계 시각은 다음 기간이다. 기준 시각 전의 시각도 그 앞 기간으로 나눈다. 정산 대상에 `[period_start, period_end)`를 저장한다.

## 정산 확정(`Settlement`, STL-2, STL-4)
- 정산 작업(`SettlementJob`)은 `openmarket.settlement.job.cron`(기본 매주 월요일 03:00)에 돌고 ShedLock(`settlement-confirm`)으로 한 인스턴스에서만 돈다. 끝난 지 `openmarket.settlement.job.close-delay`(기본 3시간)가 지난 기간 중 확정하지 않은 대상이 있는 기간을 오래된 순으로 확정한다. 늦게 오는 이벤트를 기다리는 여유다.
- 확정은 판매자마다 트랜잭션을 따로 연다. 그 판매자·기간의 확정되지 않은 대상을 모아 정산(`CONFIRMED`)을 만들고, 대상에 정산 ID를 남기고, 같은 트랜잭션에서 `SettlementConfirmed`를 낸다. 금액은 대상 금액의 합이다.
- 판매자·기간 유니크 `(seller_id, period_start)`로 정산은 한 번만 생긴다. 작업을 다시 돌리면 이미 확정한 판매자·기간은 넘기므로 정산 금액이 같다(STL-4).
- 관리자는 작업을 바로 돌린다(`POST /v1/admin/settlement-runs`). 결과는 같다. E2E가 이것으로 두 번 돌린다.
  - 즉시 실행은 ShedLock 없이 같은 유스케이스(`RunSettlementUseCase`)를 부른다. 정기 작업과 겹쳐도 판매자 잠금과 판매자·기간 유니크로 결과가 같다.
  - 작업은 끝난 기간의 확정되지 않은 판매자·기간을 기간 시작, 판매자 ID 순으로 500개씩 읽어 하나씩 확정한다. 한 번 돌면서 같은 판매자·기간을 두 번 시도하지 않는다.
  - E2E는 마감 지연도 환경 변수 `SETTLEMENT_CLOSE_DELAY`(config-repo 기본 `3h`)로 줄인다. 크론은 `openmarket.settlement.job.cron`(한국 시간)이다.
- 판매자는 확정된 정산과 그 내역, 아직 확정되지 않은 대상(정산 예정 금액과 내역)을 본다(STL-2).

| 상태 | 뜻 | 다음 |
|---|---|---|
| `CONFIRMED` | 배치가 확정했다. 지급을 기다린다 | `PAID_OUT` |
| `PAID_OUT` | 관리자가 지급 완료로 기록했다. 끝 상태 | |

## 지급 처리(STL-3, ADM-3)
- 관리자가 확정된 정산을 지급 완료로 바꾼다. 지급 시각과 관리자 ID를 남기고 `SettlementPaidOut`을 낸다. 감사 로그(`SETTLEMENT_PAID_OUT`)를 남긴다.
- 이미 `PAID_OUT`이면 204, 이벤트 없음이다. 정산 행을 잠그고 바꾼다.
- 감사 로그 대상은 `SETTLEMENT`이고 `detail`에 정산 금액(예: `18000원`)을 남긴다. 정산에 `paid_out_by`(관리자 ID)를 저장한다.

## API
| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/seller/settlements` | 판매자. 확정된 정산. 기간 최근 순 커서 목록 |
| GET | `/v1/seller/settlements/{settlementId}` | 판매자. 정산 한 건. 다른 판매자의 정산이면 404 `SETTLEMENT_NOT_FOUND` |
| GET | `/v1/seller/settlements/{settlementId}/items` | 판매자. 정산 내역(정산 대상과 주문 항목). 구매확정 최근 순 커서 목록 |
| GET | `/v1/seller/settlement-items/pending` | 판매자. 아직 확정되지 않은 대상(정산 예정 내역). 커서 목록 |
| GET | `/v1/seller/settlement-items/pending/summary` | 판매자. 정산 예정 금액 `{itemCount, salesAmount, commissionAmount, sellerCouponDiscount, settlementAmount}`. 대상이 없으면 0 |
| GET | `/v1/admin/settlements` | 관리자. `status`, `sellerId`로 거르는 쪽 번호 목록(최근 확정 순) |
| GET | `/v1/admin/settlements/{settlementId}`, `/items` | 관리자. 정산 한 건과 내역 |
| POST | `/v1/admin/settlement-runs` | 관리자. 정산 작업 즉시 실행. 200 `{confirmedCount}` |
| POST | `/v1/admin/settlements/{settlementId}/payout` | 관리자. 지급 처리. 204 |

- 정산은 `{settlementId, sellerId, periodStart, periodEnd, itemCount, salesAmount, commissionAmount, sellerCouponDiscount, amount, status, confirmedAt, paidOutAt}`이다. `periodEnd`는 기간에 들지 않는다.
- 정산 대상은 `{sellerOrderId, orderId, confirmedAt, periodStart, periodEnd, salesAmount, commissionAmount, sellerCouponDiscount, settlementAmount, lines}`이고 `lines`는 주문 항목(상품명, 카테고리, 수량, 금액, 고정한 요율, 수수료)이다. 주문 항목은 쪽마다 한 번 더 읽는다(N+1 없음).
- 커서 목록은 `?cursor=&size=`(기본 20, 최대 50)이고 틀린 커서는 400 `SETTLEMENT_INVALID_CURSOR`다. 정산 계좌는 member에 있어 응답에 싣지 않는다. 화면이 BFF로 함께 보인다.

## 이벤트
필드는 [context-map.md](context-map.md) "settlement.settlement".
| 받는 이벤트 | 처리 |
|---|---|
| `PurchaseConfirmed`(fulfillment.shipment) | 정산 대상. 같은 토픽의 다른 이벤트는 `type` 헤더로 넘긴다 |

| 내는 이벤트 | 언제 |
|---|---|
| `SettlementConfirmed` | 배치 확정(판매자·기간마다) |
| `SettlementPaidOut` | 지급 처리 |

- 리스너는 처리 기록으로 같은 이벤트를 한 번만 처리한다. 리스너 이름은 `settlement.purchase-confirmed`다.
