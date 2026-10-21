# 0018. 토스 결제는 주문서형 SDK v2와 서버 승인, 웹훅 대신 조회로 연동하고 E2E는 가짜 PG로 돌린다
- 상태: 승인
- 날짜: 2026-10-20

## 맥락
PAY-1, PAY-3, PAY-4를 토스페이먼츠 테스트 환경으로 구현한다(R9). 로컬에서만 돌리므로 공인 주소가 없어 토스 웹훅을 받을 수 없다.
주문서형(구 결제위젯) 연동 키(`test_gck`, `test_gsk`)는 전자결제 신청(사업자 정보)을 마쳐야 발급된다. 가입만 하면 나오는 API 개별 연동 키(`test_ck`, `test_sk`)로는 주문서형을 쓸 수 없다.
M3 완료 기준 E2E에는 "만료 직후 승인된 결제는 자동 취소된다"가 있다. 승인 응답이 주문 만료보다 늦게 와야 재현되는데, 실제 토스로는 응답 시각을 조절할 수 없다. 테스트 결제의 인증 단계는 카드사의 실제 인증 화면이나 토스 앱 QR이라 자동화할 수 없다(R9, [risks.md](../architecture/risks.md) "토스 테스트 환경 확인"). (2026-10-21 추가) 퀵계좌이체 테스트 흐름은 휴대폰 번호가 미리 들어 있고 테스트 비밀번호를 키패드로 누르면 끝나서 브라우저 자동화로 승인까지 갈 수 있다. 그래도 승인 응답 시각은 조절할 수 없어 경합 재현에는 가짜 PG가 필요하다.

## 결정
- 키: 토스 문서와 공식 샘플에 공개된 **문서용 테스트 키**(`test_gck_docs_…`, `test_gsk_docs_…`)를 `.env.example`에 둔다. 공개 값이라 비밀이 아니다. 내 상점 주문서형 키가 생기면 `.env`만 바꾼다. 클라이언트 키만 브라우저로 가고, 시크릿 키는 payment 서비스만 갖는다.
- 브라우저: 주문서형 JavaScript SDK v2(`@tosspayments/tosspayments-sdk`)의 `loadTossPayments` → `widgets` → `requestPayment`(Redirect 방식)를 쓴다.
- 승인은 서버가 한다. `successUrl`로 돌아온 `paymentKey`, `orderId`, `amount`를 BFF가 payment의 승인 API에 넘긴다. payment는 금액을 주문 금액과 대조한 뒤 토스 `POST /v1/payments/confirm`을 부른다([messaging.md](../architecture/messaging.md) "주문·결제 Saga").
- 토스 요청(승인, 취소)에는 토스 `Idempotency-Key` 헤더를 붙인다. 승인은 결제 ID, 취소는 취소 요청 ID로 만들어 재시도해도 한 번만 처리된다. 토스는 이 키를 15일 동안 기억한다.
- 웹훅은 쓰지 않는다. 승인 응답이 타임아웃이거나 `ALREADY_PROCESSED_PAYMENT`이면 `GET /v1/payments/{paymentKey}`로 실제 상태를 확인한다. 확인하지 못한 결제는 `IN_PROGRESS`로 두고 재조회 작업(1분)이 처리한다. `orderId` 조회(`/v1/payments/orders/{orderId}`)는 문서용 키에서 404(`NOT_FOUND_MERCHANT`)라 쓰지 않는다.
- 토스 응답의 해석
  - 금액 대조와 기록은 `totalAmount`로 한다. 결제수단 즉시 할인이 있어도 이 값은 요청 금액이다.
  - 전액 취소 여부는 `status`가 아니라 `balanceAmount == 0`으로 판단한다. 부분취소 뒤 나머지를 취소하면 `status`가 `PARTIAL_CANCELED`로 남는다.
  - 원장(PAY-4)에는 요청 종류, 토스 응답 코드, `paymentKey`, 거래 키(`lastTransactionKey`, `cancels[].transactionKey`), 금액, 시각을 남긴다. 응답 원문에서 카드 번호 같은 결제 수단 상세는 저장하지 않는다.
- 토스 API 형식은 payment의 `infra` 어댑터(`TossPaymentGatewayAdapter`) 밖으로 내보내지 않는다(ACL, [context-map.md](../architecture/context-map.md)).
- 토스 연동의 테스트
  - 서비스 통합 테스트는 WireMock으로 토스 API를 흉내 낸다(성공, 거절, 타임아웃, `ALREADY_PROCESSED_PAYMENT`, 409 `IDEMPOTENT_REQUEST_PROCESSING`). 응답 본문은 R9에서 받은 실제 응답을 본뜬다.
  - 실제 토스 테스트 환경은 R9와 결제 PR마다 손으로 확인한다. 오류는 `TossPayments-Test-Code` 헤더로 재현한다.
  - 마일스톤 E2E는 가짜 PG 컨테이너로 돌린다. 가짜 PG는 토스 API(승인, 조회, 취소)와 SDK v2 스크립트의 쓰는 부분만 흉내 낸다. shop은 `loadTossPayments(clientKey, { src })`의 `src`, payment는 토스 API 주소만 설정으로 바꾼다. 앱 코드에 E2E 분기를 두지 않는다. 승인 응답 지연은 가짜 PG의 제어 API로 시나리오가 정한다.

## 결과
- 좋아지는 점
  - 사업자 정보 없이, 공인 주소 없이 결제 상태를 확정할 수 있다. 상태를 확정하는 경로가 승인 응답과 `paymentKey` 조회 둘뿐이다.
  - 경합(만료 직후 승인), 타임아웃, 거절을 E2E에서 매번 같은 순서로 재현한다. 외부 네트워크가 없어도 E2E가 돈다.
- 감수하는 점
  - 문서용 키는 여러 사람이 함께 쓰는 상점이다. 개발자센터에서 테스트 결제 내역을 볼 수 없고, 원장이 유일한 기록이다. 카카오페이는 테스트할 수 없다.
  - E2E는 실제 토스 화면과 응답을 거치지 않는다. 토스 응답 형식이 바뀌면 E2E가 아니라 손 확인이 잡는다.
  - 재조회 작업이 결제 상태 변화를 최대 1분 늦게 반영한다. 사용자 대면 흐름은 승인 API의 동기 응답으로 끝나므로 재조회는 타임아웃 같은 예외 경로에서만 쓰인다.
  - 가상계좌처럼 입금 통보를 웹훅으로 받는 결제수단은 받지 않는다. 문서용 키의 결제 UI에는 가상계좌가 없다. 내 상점 키로 바꾸면 결제 UI 설정에서 빼야 한다.
- 후속 작업: (2026-10-21 완료) 가짜 PG는 `infra/fake-pg`(의존성 없는 Node 서버, Compose `e2e` profile, 포트 8090)다. 제어 API와 쓰는 법은 [dev-setup.md](../dev-setup.md) "E2E"에 있다. payment 쪽 연동 규칙은 [payment.md](../architecture/payment.md)에 있다.

## 검토한 대안
- **API 개별 연동 키 + 결제창(구버전) SDK**: 가입만으로 내 키를 받고 테스트 결제 내역을 볼 수 있다. 하지만 PRD PAY-1(결제위젯)과 다르고, 결제수단 선택 화면을 직접 만들어야 한다.
- **E2E도 실제 토스**: 실제 연동을 매번 확인하지만 경합을 재현할 수 없고, 인증 단계(카드사 화면, 앱 QR)를 자동화할 수 없다. 외부 네트워크와 분당 100건 제한에 묶인다.
- **웹훅 + 터널(ngrok 등)**: 비용 0 원칙과 로컬 재현성(NFR-12)에 맞지 않는다.
- **E2E 전용 앱 분기(`NEXT_PUBLIC_PG_MODE=fake`)**: 시나리오가 실제 SDK 호출 경로를 지나지 않는다. SDK 주소만 바꾸면 앱 코드는 같다.
