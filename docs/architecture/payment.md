# 결제 (payment 서비스)

토스페이먼츠 결제 승인, 결제 전 취소된 주문의 보상 취소, 결제 원장을 갖는다(PAY-1, PAY-3, PAY-4). 토스 연동 방식은 [ADR 0018](../adr/0018-toss-payments-integration.md), 주문 흐름 전체(Saga)는 [messaging.md](messaging.md) "주문·결제 Saga"에 있다. 부분취소 환불(PAY-2)은 M4에서 이 문서에 더한다.

## 결제(`Payment`)
주문 하나당 하나다. 주문 ID, 구매자 ID, 결제 금액, 주문명을 주문에서 받아 고정한다. 금액은 이후 바뀌지 않는다.

| 상태 | 뜻 | 다음 상태 |
|---|---|---|
| `READY` | 결제를 기다린다 | `IN_PROGRESS`, `CANCELLED` |
| `IN_PROGRESS` | 토스에 승인을 요청했고 결과를 기다린다 | `APPROVED`, `FAILED`, `CANCELLED`(취소 요청 뒤 거절) |
| `APPROVED` | 승인됐다 | `CANCELLED`(보상 취소) |
| `FAILED` | 토스가 승인을 거절했다. 같은 주문으로 다시 승인할 수 있다 | `IN_PROGRESS`, `CANCELLED` |
| `CANCELLED` | 주문이 결제 전에 취소돼 결제를 무효로 했거나, 승인된 결제를 토스에서 전액 취소했다 | |

- 상태를 바꿀 때는 결제 행을 비관적 락(`SELECT … FOR UPDATE`)으로 잡는다. 같은 주문의 승인, 재조회, 취소가 겹쳐도 한 번에 하나만 바꾼다.
- 토스 호출은 락과 트랜잭션 밖에서 한다. 승인 요청 전에 `IN_PROGRESS`로 커밋하고, 응답을 받으면 다시 잠그고 반영한다. 그사이 재조회 작업이 먼저 반영했으면 늦게 온 결과는 버린다(승인 시도 번호로 가린다).

### 결제 준비
`order.order`의 `OrderAwaitingPayment`(orderId, buyerId, amount, orderName)를 받아 `READY` 결제를 만든다. 리스너는 처리 기록(`processed_event`)으로 거르고, 같은 주문의 결제가 이미 있으면 아무것도 하지 않는다. 같은 토픽의 `OrderCancelled`는 아래 "보상 취소"에서 받고, 그 밖의 이벤트는 받지 않는다.

## 승인(PAY-1)
`POST /v1/payments/confirm`(`Idempotency-Key` 필수). 구매자의 BFF가 토스 `successUrl`로 받은 값을 그대로 넘긴다. 주체는 토큰의 `sub`(= buyerId)다.

| 본문 | 규칙 |
|---|---|
| `orderId` | 필수 |
| `paymentKey` | 1~200자 |
| `amount` | 0 이상. 결제 금액(원) |

1. 결제를 잠그고 확인한다.
   | 확인 | 어기면 |
   |---|---|
   | 주문의 결제가 있고 본인 것이다 | 404 `PAYMENT_NOT_FOUND`. 결제 준비 전(주문이 `AWAITING_PAYMENT`가 되기 전)도 같다 |
   | 주문이 취소되지 않았다(결제가 `CANCELLED`가 아니고 취소 요청이 없다) | 409 `PAYMENT_CANCELLED`. 같은 결제 키여도 같다 |
   | 결제가 `READY` 또는 `FAILED`다 | 아래 "이미 승인을 요청한 결제" |
   | `amount`가 결제 금액과 같다 | 422 `PAYMENT_AMOUNT_MISMATCH`. 토스를 부르지 않고 결제는 그대로다 |
2. `IN_PROGRESS`로 바꾸고 `paymentKey`, 승인 시도 번호(1부터), 요청 시각을 남기고 커밋한다.
3. 토스 승인 API를 부른다. 토스 `Idempotency-Key`는 `<결제 ID>-<승인 시도 번호>`다. 같은 시도를 다시 보내도 한 번만 승인되고, 거절된 뒤 새 `paymentKey`로 다시 결제하면 새 키를 쓴다.
4. 결과를 반영한다(아래 표). 원장에 남긴다.

| 토스 응답 | 처리 | 응답 |
|---|---|---|
| 200 | `APPROVED` + `PaymentApproved` | 200 `{paymentId, orderId, status, amount, method}` |
| 400 `ALREADY_PROCESSED_PAYMENT`, 409 `IDEMPOTENT_REQUEST_PROCESSING`, 5xx, 연결 실패, 타임아웃 | 실패로 보지 않는다. `paymentKey`로 결제를 조회해 반영한다(아래 "조회 결과"). 조회로도 정하지 못하면 `IN_PROGRESS`로 두고 재조회 작업이 처리한다 | 정해졌으면 그 결과, 아니면 202(`status`가 `IN_PROGRESS`) |
| 그 밖의 4xx(카드 거절, 결제 세션 만료 등) | `FAILED` + `PaymentFailed` | 422 `PAYMENT_REJECTED`, `pgCode`(토스 오류 코드), `pgMessage`(토스 오류 메시지) |

- 202를 받은 화면은 주문 상세를 다시 읽어 결과를 기다린다. 주문 상태는 order가 `PaymentApproved`를 반영해 바꾼다.
- 토스를 부르는 동안 주문이 취소되면(아래 "보상 취소") 토스 응답과 상관없이 409 `PAYMENT_CANCELLED`다. 승인됐으면 취소 작업이 돌려준다.
- 토스 거절 메시지는 구매자에게 보여 줄 수 있는 문장이고 개인정보가 없다. 그대로 넘긴다.
- 금액 대조와 기록은 토스 응답의 `totalAmount`로 한다. 결제수단 즉시 할인이 있어도 요청 금액 그대로다(R9).

### 이미 승인을 요청한 결제
| 결제 상태 | 같은 `paymentKey` | 다른 `paymentKey` |
|---|---|---|
| `IN_PROGRESS` | 202. 토스를 다시 부르지 않는다 | 409 `PAYMENT_NOT_CONFIRMABLE` |
| `APPROVED` | 200. 첫 승인 결과 | 409 `PAYMENT_NOT_CONFIRMABLE` |

- 같은 `Idempotency-Key`로 다시 보내면 첫 응답을 그대로 받는다(5xx 제외, NFR-2). 위 표는 키를 새로 만들어 다시 보낸 경우다.

### 조회 결과
승인 응답으로 정하지 못한 결제는 `GET /v1/payments/{paymentKey}`로 정한다(ADR 0018).
| 토스 결제 상태 | 처리 |
|---|---|
| `DONE`, `CANCELED`, `PARTIAL_CANCELED` | 승인됐다. `APPROVED` + `PaymentApproved`. 취소된 상태는 승인 뒤의 일이라 승인으로 본다 |
| `ABORTED`, `EXPIRED` | `FAILED` + `PaymentFailed`(코드는 토스 상태) |
| `READY`, `IN_PROGRESS`, `WAITING_FOR_DEPOSIT` | 아직 정해지지 않았다. `IN_PROGRESS`로 둔다 |
| 404 | 토스에 결제가 없다. 돈이 빠져나가지 않았으므로 `FAILED` + `PaymentFailed`(토스 오류 코드) |
| 그 밖의 오류, 타임아웃 | 정하지 못했다. `IN_PROGRESS`로 둔다 |

### 재조회 작업(`PaymentReconcileJob`)
- 1분마다(`openmarket.payment.reconcile-interval`) 돌고 ShedLock(`payment-reconcile`)으로 한 인스턴스에서만 돈다.
- 승인을 요청한 지 1분(`openmarket.payment.reconcile-after`)이 지난 `IN_PROGRESS` 결제를 오래된 순으로 100건씩 읽어, 결제마다 토스를 조회하고 위 "조회 결과"대로 반영한다. 1분은 승인 요청의 읽기 타임아웃보다 길어서, 아직 응답을 기다리는 요청과 겹치지 않는다.
- 다 못 하면 다음 주기에 이어 한다. 정해지지 않은 결제는 다음 주기에 다시 조회한다.

## 보상 취소(PAY-3)
order가 결제 전에 주문을 취소하면(`OrderCancelled`: 재고 부족, 만료, 구매자 취소) 결제를 무효로 하고, 이미 승인됐으면 토스에서 전액 취소한다. 결제가 승인됐는데 주문이 취소돼 돈만 빠져나가는 일을 막는다.

### `OrderCancelled` 수신
리스너가 처리 기록으로 거르고 결제 행을 잠가 바꾼다. 토스는 부르지 않는다(리스너 트랜잭션 안이다).
| 결제 상태 | 처리 |
|---|---|
| 없음 | 넘긴다. 예약 중에 취소된 주문이라 결제가 생기지 않는다. 같은 토픽에서 `OrderAwaitingPayment`가 `OrderCancelled`보다 먼저 오므로, 결제가 생긴 뒤에 취소가 오는 순서는 뒤집히지 않는다 |
| `READY`, `FAILED` | `CANCELLED`. 돈이 빠져나가지 않았으므로 토스를 부르지 않고 이벤트도 내지 않는다 |
| `IN_PROGRESS` | 취소 요청(`cancel_requested_at`)을 남긴다. 승인되면 아래 취소 작업 대상이 되고, 거절되면 `FAILED`가 아니라 `CANCELLED`다(다시 결제할 주문이 없다. `PaymentFailed`도 내지 않는다) |
| `APPROVED` | 취소 요청을 남긴다. 취소 작업 대상이다 |
| `CANCELLED`, 취소 요청 있음 | 넘긴다 |

- 취소 요청이 있는 결제는 승인을 요청할 수 없다(409 `PAYMENT_CANCELLED`). 승인 응답을 기다리던 결제의 결과는 그대로 반영한다. 승인되면 `PaymentApproved`를 내고, order는 이미 `CANCELLED`라 경고 로그만 남긴다([order.md](order.md) "결제 결과").

### 취소 작업(`PaymentCancelJob`)
- 1분마다(`openmarket.payment.cancel-interval`) 돌고 ShedLock(`payment-cancel`)으로 한 인스턴스에서만 돈다. 취소 요청이 있고 토스가 거절한 적 없는 `APPROVED` 결제를 오래된 요청 순으로 100건씩 읽는다.
- 결제마다 토스 `POST /v1/payments/{paymentKey}/cancel`(전액, `cancelReason` "주문 취소")을 트랜잭션 밖에서 부르고, 원장에 남긴 뒤 결제를 다시 잠가 반영한다. 토스 `Idempotency-Key`는 `<결제 ID>-cancel`이다. 전액 취소는 결제마다 한 번이라 결제 ID로 만든다(부분취소 환불은 M4에서 환불 ID로 만든다).
- 리스너에서 바로 부르지 않고 작업으로 미룬 것은, 토스 호출을 리스너 트랜잭션 밖에 두고 실패하면 같은 경로로 다시 시도하기 위해서다. 보상 취소는 드문 경로라 최대 1분 늦어도 된다. E2E는 주기를 짧게 준다.

| 토스 응답 | 처리 |
|---|---|
| 200, 남은 금액(`balanceAmount`) 0 | `CANCELLED` + `PaymentCancelled`. 전액 취소인지는 상태가 아니라 남은 금액으로 정한다(ADR 0018) |
| 400 `ALREADY_CANCELED_PAYMENT` | 이미 취소됐다. 위와 같다 |
| 5xx, 연결 실패, 타임아웃, 400 `PROVIDER_ERROR`, 403 `FORBIDDEN_CONSECUTIVE_REQUEST`, 409 `IDEMPOTENT_REQUEST_PROCESSING` | 정하지 못했다. `APPROVED`로 두고 다음 주기에 같은 멱등 키로 다시 부른다 |
| 그 밖의 4xx(`NOT_CANCELABLE_PAYMENT` 등), 200인데 남은 금액이 있다(`BALANCE_REMAINING`) | 토스가 취소를 거절했다. 실패 코드(`cancel_failure_code`)를 남기고 `APPROVED`로 둔다. 자동으로 다시 부르지 않고 ERROR 로그를 남긴다. 운영자가 처리하는 방법은 M4에서 환불 실패(`RefundFailed`)의 관리자 재시도와 함께 정한다 |

## 토스 연동(`TossPaymentGatewayAdapter`)
- 토스 API 형식은 `infra` 어댑터 밖으로 내보내지 않는다(ACL). `command.application`의 `PaymentGatewayPort`는 승인·조회 결과로 승인됨, 거절됨, 미정, 알 수 없음을, 취소 결과로 취소됨, 취소 거절, 알 수 없음을 안다.
- 주소 `openmarket.payment.toss.base-url`(기본 `https://api.tosspayments.com`), 시크릿 키 `TOSS_SECRET_KEY`(Basic 인증). E2E는 주소만 가짜 PG로 바꾼다.
- 타임아웃: 연결 2초, 읽기 10초. R9에서 승인은 1초 안팎이었다. 타임아웃이 나도 조회로 정하므로 사용자가 오래 기다리지 않게 짧게 둔다.
- 토스는 Eureka 밖의 외부 API라 OpenFeign·서킷 브레이커를 쓰지 않는다. 실패는 조회와 재조회 작업이 메운다.

## 원장(`PaymentLedgerEntry`, PAY-4)
토스 요청 하나마다 한 줄을 추가한다. 고치거나 지우지 않는다.
| 필드 | 내용 |
|---|---|
| `kind` | `CONFIRM`(승인), `QUERY`(조회), `CANCEL`(전액 취소) |
| `outcome` | `APPROVED`, `REJECTED`, `PENDING`, `UNKNOWN`, `CANCELLED` |
| `pg_code` | 토스 오류 코드, 또는 토스 결제 상태. 응답이 없으면 `TIMEOUT`, `CONNECTION_FAILED` |
| `payment_key`, `transaction_key` | 토스 결제 키, 거래 키(승인·조회는 `lastTransactionKey`, 취소는 마지막 `cancels[].transactionKey`) |
| `amount` | 승인된 금액(`totalAmount`). 취소는 비워 둔다(전액이다) |
| `occurred_at` | 응답을 받은(실패한) 시각 |

- 응답 원문은 저장하지 않는다. 카드 번호 같은 결제수단 상세가 들어 있다.

## 이벤트
`payment.payment` 토픽, 키는 orderId다. 필드는 [context-map.md](context-map.md) "payment.payment".
| 이벤트 | 언제 |
|---|---|
| `PaymentApproved` | 승인 응답이나 조회로 승인을 확인했다 |
| `PaymentFailed` | 토스가 거절했거나 조회로 실패를 확인했다. 취소 요청이 있는 결제면 내지 않는다 |
| `PaymentCancelled` | 승인된 결제를 토스에서 전액 취소했다(보상 취소). 돈이 빠져나가지 않은 결제를 무효로 할 때는 내지 않는다 |

## API
gateway 경로는 `/api/payment` + 아래 경로다.
| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/v1/payments/confirm` | 승인. 위 "승인" |
