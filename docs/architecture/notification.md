# 알림 (notification 서비스)

이메일과 인앱 알림을 보낸다(NTF-1). 같은 이벤트로 알림이 두 번 가지 않게 한다(NTF-2). 이메일은 로컬 Mailpit(SMTP 1025, 화면 http://localhost:8025)으로 받는다.
가입 인증 메일, 인앱 알림, 인앱 알림 종류의 일부를 같은 문구로 보내는 회원 이메일이 있다. 인앱 알림은 조회 방식이다. 앱이 목록과 안 읽은 수를 읽고, 실시간 푸시는 하지 않는다(2026-10-21 결정).

## 흐름 (이메일)
1. 리스너(`ui.event`)가 원인 이벤트를 받는다. 같은 토픽의 다른 이벤트는 `type` 헤더를 보고 처리 기록 없이 넘긴다.
2. `IdempotentEventProcessor` 트랜잭션 안에서 유스케이스가 `Notification`(`PENDING`)을 저장한다. 템플릿에 넣을 값은 `notification_param`에 둔다.
3. 발송 작업(`EmailDispatchJob`, 2초마다, ShedLock)이 보낼 차례인 알림을 50개씩 찾아 하나씩 보낸다. 알림마다 트랜잭션을 따로 연다. 받는 주소가 없는 알림은 이때 member에서 조회한다(아래 "이메일 받는 주소").
4. 보내면 `SENT`로 바꾸고 `notification_param`을 지운다.

SMTP와 member 조회는 이벤트 처리 트랜잭션 밖에 둔다. 둘이 느리거나 멈춰도 Kafka 소비가 밀리지 않는다.

## 이메일 받는 주소
- 가입 인증 메일은 원인 이벤트의 주소로 보내고 `recipient_email`에 저장한다(가입 직후라 member에 회원이 아직 없을 수 있다).
- 회원 알림 이메일(아래 "인앱 알림" 표의 이메일 열)은 주소를 저장하지 않는다. 발송 작업이 보낼 때 member 내부 API `GET /internal/v1/members/{memberId}/contact`로 조회하고([member.md](member.md) "내부 API") 그 자리에서만 쓴다. 이벤트에 구매자 개인정보를 싣지 않는 규칙([context-map.md](context-map.md))을 지키고, 주소가 바뀌어도 최신 주소로 간다.
  - 호출 방식(OpenFeign, `client_credentials` 클라이언트 ID `notification`, 타임아웃, 연결 실패만 한 번 재시도, 서킷 브레이커)은 order의 catalog 조회와 같다([order.md](order.md) "catalog 조회"). 값은 config-repo `notification.yml`에 있다.
  - 회원이 없거나 탈퇴했으면(404), 또는 이메일을 인증하지 않았으면 보내지 않고 `SKIPPED`로 끝낸다.
  - member를 부를 수 없으면(타임아웃, 5xx, 서킷 열림) SMTP 실패와 같이 다시 시도한다.

## 중복과 재시도
- 중복 방지는 세 겹이다. 리스너의 `processed_event`(이벤트 ID, 리스너), 유스케이스의 존재 확인, DB 유니크 제약 `(원인 이벤트, 채널, 받는 쪽, 받는 사람)`.
- 보낼 때는 최소 한 번이다. SMTP가 받은 직후 상태를 저장하기 전에 프로세스가 죽으면, 다음 실행에서 한 번 더 보낸다.
- SMTP가 실패하면 30초, 1분, 2분, 4분 뒤에 다시 시도한다. 5번째 실패에서 `FAILED`가 되고 `notification_param`을 지운다. 로그에는 알림 종류와 ID만 남긴다.
- 실측(2026-10-18): Mailpit을 멈춘 채 가입하자 알림이 `PENDING`(시도 1회)으로 남았고, 다시 띄우자 약 30초 뒤 `SENT`가 됐다.

## 템플릿
- 템플릿은 `templates/mail/<이름>.html`과 `.txt` 두 개다(Thymeleaf). 메일은 HTML과 텍스트를 함께 보낸다.
- 가입 인증은 `email-verification`이고, 회원 알림 이메일은 모두 `notice` 하나다. `notice`는 인앱 문구(`message`)와 앱 화면 링크(`link`)를 넣는다.
- 제목은 `ThymeleafEmailRenderer`에 종류별로 둔다.
- 앱 화면 링크는 `ConfiguredAppLinks`가 받는 쪽의 앱 주소(`openmarket.notification.app-urls`)와 이동 대상으로 만든다. 화면은 아래 "이동 대상 → 앱 화면" 표와 같다(앱의 알림 이동과 같은 경로). 이동 대상이 없으면 앱의 첫 화면이다.
- 값 이름이 `At`으로 끝나면 한국 시간(`yyyy년 M월 d일 HH:mm`)으로 바꿔 넣는다.

| 종류 | 원인 이벤트 | 값 |
|---|---|---|
| `EMAIL_VERIFICATION` | `EmailVerificationRequested`(auth) | `verificationUrl`, `expiresAt` |
| 회원 알림 이메일 종류 | 아래 "인앱 알림" 표 | `message`, `link` |

## 인앱 알림
알림 하나는 받는 쪽(`audience`) 하나에 속한다. 받는 쪽은 앱과 같다.

| 받는 쪽 | 앱 | 받는 사람 | API 경로 |
|---|---|---|---|
| `BUYER` | shop | 회원 한 명(`recipient_account_id`) | `/v1/notifications` |
| `SELLER` | seller | 판매자 한 명(= 회원 ID) | `/v1/seller/notifications` |
| `ADMIN` | admin | 관리자 전체(역할 대상, 받는 사람 없음) | `/v1/admin/notifications` |

- 관리자 알림은 역할에 한 건만 저장하고, 관리자라면 누구나 같은 목록을 본다(2026-10-22 결정). notification은 관리자 명단을 모른다. 나중에 생긴 관리자도 지난 알림을 본다.
- 읽음은 `notification_read`(알림, 읽은 회원)에 따로 둔다. 관리자 알림도 관리자마다 따로 읽는다.
- 같은 회원이 구매자와 판매자이면 shop과 seller가 각자의 알림만 보인다. 입점 반려는 판매자가 아니므로 `BUYER`로 간다.
- 문구는 알림을 만들 때 정해 `message`에 저장한다(`InAppMessages`). 앱은 문구를 그대로 보이고, `targetType`·`targetId`로 이동할 화면을 정한다.
- 인앱 알림은 만들 때 바로 `SENT`다. 발송 작업을 거치지 않는다.

API(조회 방식)
- `GET .../notifications?cursor=&size=`: 최근 순 커서 목록(`{items, totalCount, nextCursor}`). 항목에 `read`가 있다. 커서가 틀리면 400 `NOTIFICATION_INVALID_CURSOR`
- `GET .../notifications/unread-count`: `{count}`. 앱 헤더가 페이지 이동 때와 주기적으로 읽는다
- `POST .../notifications/{id}/read`(204): 다시 해도 204. 내 알림이 아니면 404 `NOTIFICATION_NOT_FOUND`
- `POST .../notifications/read-all`(204): 지금까지 온 알림을 모두 읽음으로

| 종류 | 받는 쪽 | 원인 이벤트 | 이동 대상 | 이메일 |
|---|---|---|---|---|
| `ORDER_PAID` | `BUYER` | `OrderPaid` | `ORDER` | 보냄 |
| `SHIPMENT_SHIPPED` | `BUYER` | `ShipmentShipped`(교환 재발송 포함) | `ORDER` | 보냄 |
| `SHIPMENT_DELIVERED` | `BUYER` | `ShipmentDelivered` | `ORDER` | |
| `CLAIM_APPROVED` | `BUYER` | `ClaimApproved`(반품·교환만. 취소는 신청과 함께 승인된다) | `ORDER` | 보냄 |
| `CLAIM_REJECTED` | `BUYER` | `ClaimRejected`(판매자 거부, 중재 거부 확정) | `ORDER` | 보냄 |
| `CLAIM_COMPLETED` | `BUYER` | `ClaimCompleted` | `ORDER` | 보냄 |
| `SELLER_REJECTED` | `BUYER` | `SellerRejected` | `SELLER_APPLICATION` | 보냄 |
| `SELLER_ORDER_RECEIVED` | `SELLER` | `OrderPaid`(하위 주문마다) | `SELLER_ORDER` | |
| `CLAIM_REQUESTED` | `SELLER` | `ClaimRequested`(취소 포함) | `SELLER_ORDER` | |
| `SELLER_APPROVED` | `SELLER` | `SellerApproved` | 없음 | 보냄 |
| `PRODUCT_BANNED` | `SELLER` | `ProductBanned` | `PRODUCT` | 보냄 |
| `REVIEW_CREATED` | `SELLER` | `ReviewCreated` | `REVIEW` | |
| `SETTLEMENT_CONFIRMED` | `SELLER` | `SettlementConfirmed` | `SETTLEMENT` | 보냄(NTF-1 정산 확정) |
| `SETTLEMENT_PAID_OUT` | `SELLER` | `SettlementPaidOut` | `SETTLEMENT` | 보냄 |
| `CLAIM_MEDIATION_REQUESTED` | `ADMIN` | `ClaimMediationRequested` | `CLAIM` | |
| `REFUND_FAILED` | `ADMIN` | `RefundFailed` | `REFUND` | |

- 이메일은 결과를 알려야 하는 것만 보낸다(NTF-1의 주문 완료·발송·클레임 결과, 입점 심사, 상품 제재). 판매자의 새 주문·클레임 신청처럼 자주 오고 판매자 화면에서 처리하는 것과, 받는 사람이 없는 관리자 알림은 인앱만이다(2026-10-22 결정).
- 이메일 종류면 인앱 알림과 같은 트랜잭션에서 이메일 알림(`PENDING`, 주소 없음)도 만든다.

- 중복 방지는 이메일과 같다. 이벤트 하나가 여러 알림을 만들 수 있어(`OrderPaid`) 유니크 제약은 `(원인 이벤트, 채널, 받는 쪽, 받는 사람)`이다.
- `PaymentFailed`, `PaymentCancelled`는 아직 알리지 않는다. 구매자가 결제 화면에서 바로 결과를 보고, 이벤트에 구매자 ID가 없다.

M5에서 더한 것(2026-10-22 설계, 2026-10-25 구현)
- 리스너는 `notification.review-created`(review.review), `notification.settlement-confirmed`, `notification.settlement-paid-out`(settlement.settlement)이다.
- 새 리뷰는 판매자 클레임 신청처럼 자주 오고 판매자 화면에서 답하는 것이라 인앱만이다. 문구에 리뷰 본문은 넣지 않는다(상품명과 별점만).
- 정산 확정 문구는 기간을 한국 날짜로 넣는다. 기간 끝(`periodEnd`)은 기간에 들지 않으므로 그 직전 날을 끝 날로 쓴다. 예: `10월 26일~11월 1일 판매분 정산이 확정됐습니다. 정산 금액 43,417원`.
- `CLAIM_REQUESTED`는 하위 주문 상세(`SELLER_ORDER`, `ClaimRequested`의 sellerOrderId)로 간다. 클레임은 하위 주문 상세에서 처리한다. 그 전에 만든 알림은 `CLAIM`(클레임 ID)으로 남아 있어 seller는 계속 클레임 진행 중 목록으로 보낸다(M4 회고에서 넘어옴).
- 금액(2026-10-25 결정): 구매자 주문 완료는 주문 금액(= 결제 금액, 할인 뒤)이고, 판매자 새 주문은 하위 주문의 상품 금액(할인 전)이다. 판매자 주문 화면도 상품 금액을 보이고 할인 필드가 없어 같은 값을 쓴다.
- seller `REVIEW`는 리뷰 목록의 그 리뷰(`/reviews#review-{reviewId}`, M5 PR 14b), `SETTLEMENT`는 정산 상세(`/settlements/{settlementId}`, 14c)로 간다. 이메일 링크(`ConfiguredAppLinks`)도 같은 화면이다(14c에서 첫 화면 대신 바꿨다).
- notification 메모리(M4 peak 564MB/640MB, 중간 측정 470MB)는 리스너 두 개만 늘어 대기 상태로는 차이를 보기 어렵다. M5 E2E PR에서 시나리오를 돌린 뒤 서비스 15개와 함께 다시 잰다([risks.md](risks.md) R6).

화면(세 앱 같음, `packages/ui`의 `NotificationBell`, `NotificationList`). 조회 방식과 관리자 역할 대상 알림의 근거는 [ADR 0019](../adr/0019-in-app-notification-polling.md)
- 머리글 "알림" 링크에 안 읽은 수를 보인다(10개부터 "9+"). 페이지를 새로 열면 서버(레이아웃)가 읽는다. 클라이언트 이동은 레이아웃을 다시 그리지 않으므로 경로가 바뀔 때, 그리고 머무는 동안 60초마다 앱의 BFF `GET /api/notifications/unread-count`로 다시 읽는다. 알림을 읽는 Server Action은 레이아웃을 재검증한다. notification에 닿지 못하면 0으로 보여 화면이 깨지지 않는다.
- 알림함 `/notifications`는 최근 순 20개씩, "더 보기"가 다음 커서다. 알림을 열면(폼 POST) 읽음으로 하고 그 화면으로 간다. 갈 화면이 없는 알림은 "읽음"만 한다. "모두 읽음"이 있다.
- 이동 대상 → 앱 화면

  | 앱 | 대상 | 화면 |
  |---|---|---|
  | shop | `ORDER` | 주문 상세 |
  | shop | `SELLER_APPLICATION` | 입점 신청 |
  | seller | `SELLER_ORDER` | 하위 주문 상세 |
  | seller | `CLAIM` | 주문 목록의 클레임 진행 중 탭(M5 전에 만든 클레임 신청 알림. 클레임 ID만 있다) |
  | seller | `REVIEW` | 리뷰 목록의 그 리뷰(`/reviews#review-{reviewId}`) |
  | seller | `SETTLEMENT` | 정산 상세 |
  | seller | `PRODUCT` | 상품 상세 |
  | admin | `CLAIM` | 클레임 중재 |
  | admin | `REFUND` | 결제 실패 |
- 열 화면 주소는 폼 값이라 BFF가 앱 안의 경로(`/`로 시작하고 `//`가 아닌 것)만 따른다.

## 보관
- 알림은 만든 지 90일(`openmarket.notification.retention`)이 지나면 지운다. 보낼 차례를 기다리는 이메일(`PENDING`)은 남긴다. `SENT`, `FAILED`, `SKIPPED`는 지운다. 읽음 기록은 알림과 함께 지워진다.
- 정리 작업(`NotificationPurgeJob`, 1시간마다, ShedLock)이 1,000건씩 지운다.

## 개인정보
- 받는 주소는 가입 인증 메일만 저장한다. 회원 알림 이메일은 보낼 때 조회하고 저장하지 않는다(위 "이메일 받는 주소").
- 인증 링크처럼 비밀이 든 값은 발송을 끝내면 지운다. 로그와 예외 메시지에는 받는 사람과 본문을 넣지 않는다.
- 인앱 알림 문구에는 개인정보를 넣지 않는다. 상호, 송장 번호, 금액, 관리자가 쓴 사유(200자까지)만 넣는다.
