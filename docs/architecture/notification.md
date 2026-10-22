# 알림 (notification 서비스)

이메일과 인앱 알림을 보낸다(NTF-1). 같은 이벤트로 알림이 두 번 가지 않게 한다(NTF-2). 이메일은 로컬 Mailpit(SMTP 1025, 화면 http://localhost:8025)으로 받는다.
지금 만든 것은 가입 인증 메일과 인앱 알림이다. 인앱 알림 종류의 이메일 발송(연락처 조회)은 [roadmap.md](../roadmap.md) M4의 다음 단계에서 더한다. 인앱 알림은 조회 방식이다. 앱이 목록과 안 읽은 수를 읽고, 실시간 푸시는 하지 않는다(2026-10-21 결정).

## 흐름 (이메일)
1. 리스너(`ui.event`)가 원인 이벤트를 받는다. 같은 토픽의 다른 이벤트는 `type` 헤더를 보고 처리 기록 없이 넘긴다.
2. `IdempotentEventProcessor` 트랜잭션 안에서 유스케이스가 `Notification`(`PENDING`)을 저장한다. 템플릿에 넣을 값은 `notification_param`에 둔다.
3. 발송 작업(`EmailDispatchJob`, 2초마다, ShedLock)이 보낼 차례인 알림을 50개씩 찾아 하나씩 보낸다. 알림마다 트랜잭션을 따로 연다.
4. 보내면 `SENT`로 바꾸고 `notification_param`을 지운다.

SMTP는 이벤트 처리 트랜잭션 밖에 둔다. SMTP가 느리거나 멈춰도 Kafka 소비가 밀리지 않는다.

## 중복과 재시도
- 중복 방지는 세 겹이다. 리스너의 `processed_event`(이벤트 ID, 리스너), 유스케이스의 존재 확인, DB 유니크 제약 `(source_event_id, channel)`.
- 보낼 때는 최소 한 번이다. SMTP가 받은 직후 상태를 저장하기 전에 프로세스가 죽으면, 다음 실행에서 한 번 더 보낸다.
- SMTP가 실패하면 30초, 1분, 2분, 4분 뒤에 다시 시도한다. 5번째 실패에서 `FAILED`가 되고 `notification_param`을 지운다. 로그에는 알림 종류와 ID만 남긴다.
- 실측(2026-10-18): Mailpit을 멈춘 채 가입하자 알림이 `PENDING`(시도 1회)으로 남았고, 다시 띄우자 약 30초 뒤 `SENT`가 됐다.

## 템플릿
- 종류(`NotificationKind`)마다 `templates/mail/<종류>.html`과 `.txt` 두 개를 둔다(Thymeleaf). 메일은 HTML과 텍스트를 함께 보낸다.
- 제목은 `ThymeleafEmailRenderer`에 종류별로 둔다.
- 값 이름이 `At`으로 끝나면 한국 시간(`yyyy년 M월 d일 HH:mm`)으로 바꿔 넣는다.

| 종류 | 원인 이벤트 | 값 |
|---|---|---|
| `EMAIL_VERIFICATION` | `EmailVerificationRequested`(auth) | `verificationUrl`, `expiresAt` |

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

| 종류 | 받는 쪽 | 원인 이벤트 | 이동 대상 |
|---|---|---|---|
| `ORDER_PAID` | `BUYER` | `OrderPaid` | `ORDER` |
| `SHIPMENT_SHIPPED` | `BUYER` | `ShipmentShipped`(교환 재발송 포함) | `ORDER` |
| `SHIPMENT_DELIVERED` | `BUYER` | `ShipmentDelivered` | `ORDER` |
| `CLAIM_APPROVED` | `BUYER` | `ClaimApproved`(반품·교환만. 취소는 신청과 함께 승인된다) | `ORDER` |
| `CLAIM_REJECTED` | `BUYER` | `ClaimRejected`(판매자 거부, 중재 거부 확정) | `ORDER` |
| `CLAIM_COMPLETED` | `BUYER` | `ClaimCompleted` | `ORDER` |
| `SELLER_REJECTED` | `BUYER` | `SellerRejected` | 없음 |
| `SELLER_ORDER_RECEIVED` | `SELLER` | `OrderPaid`(하위 주문마다) | `SELLER_ORDER` |
| `CLAIM_REQUESTED` | `SELLER` | `ClaimRequested`(취소 포함) | `CLAIM` |
| `SELLER_APPROVED` | `SELLER` | `SellerApproved` | 없음 |
| `PRODUCT_BANNED` | `SELLER` | `ProductBanned` | `PRODUCT` |
| `CLAIM_MEDIATION_REQUESTED` | `ADMIN` | `ClaimMediationRequested` | `CLAIM` |
| `REFUND_FAILED` | `ADMIN` | `RefundFailed` | `REFUND` |

- 중복 방지는 이메일과 같다. 이벤트 하나가 여러 알림을 만들 수 있어(`OrderPaid`) 유니크 제약은 `(원인 이벤트, 채널, 받는 쪽, 받는 사람)`이다.
- `PaymentFailed`, `PaymentCancelled`는 아직 알리지 않는다. 구매자가 결제 화면에서 바로 결과를 보고, 이벤트에 구매자 ID가 없다.

## 보관
- 알림은 만든 지 90일(`openmarket.notification.retention`)이 지나면 지운다. 보낼 차례를 기다리는 이메일(`PENDING`)은 남긴다. 읽음 기록은 알림과 함께 지워진다.
- 정리 작업(`NotificationPurgeJob`, 1시간마다, ShedLock)이 1,000건씩 지운다.

## 개인정보
- 받는 사람 이메일은 원인 이벤트에서 받아 `notification.recipient_email`에 저장한다. 연락처를 member에서 조회(OpenFeign, `client_credentials`)하는 것은 이메일이 이벤트에 없는 알림 종류부터 쓴다.
- 인증 링크처럼 비밀이 든 값은 발송을 끝내면 지운다. 로그와 예외 메시지에는 받는 사람과 본문을 넣지 않는다.
- 인앱 알림 문구에는 개인정보를 넣지 않는다. 상호, 송장 번호, 금액, 관리자가 쓴 사유(200자까지)만 넣는다.
