# 알림 (notification 서비스)

이메일과 인앱 알림을 보낸다(NTF-1). 같은 이벤트로 알림이 두 번 가지 않게 한다(NTF-2). 이메일은 로컬 Mailpit(SMTP 1025, 화면 http://localhost:8025)으로 받는다.
지금 만든 것은 이메일과 가입 인증 메일이다. 인앱 알림과 나머지 알림 종류는 [roadmap.md](../roadmap.md) M4에서 더한다.

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

## 개인정보
- 받는 사람 이메일은 원인 이벤트에서 받아 `notification.recipient_email`에 저장한다. 연락처를 member에서 조회(OpenFeign, `client_credentials`)하는 것은 이메일이 이벤트에 없는 알림 종류부터 쓴다.
- 인증 링크처럼 비밀이 든 값은 발송을 끝내면 지운다. 로그와 예외 메시지에는 받는 사람과 본문을 넣지 않는다.
- 보낸 알림 행의 보관 기간과 정리 작업은 아직 없다. 인앱 알림을 만들 때 함께 정한다.
