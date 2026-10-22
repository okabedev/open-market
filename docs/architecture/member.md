# 회원 (member 서비스)

회원 프로필과 배송지(ACC-3), 탈퇴(ACC-4), 입점 신청·심사와 판매자(SEL-1~3)를 갖는다.
자격 증명과 역할은 auth가 갖는다([auth.md](auth.md)). 두 서비스는 같은 ID(`memberId` = `accountId`)를 쓴다.

## 회원 생성
1. auth가 가입을 받으면 `AccountRegistered`(accountId, email, name)를 낸다([context-map.md](context-map.md)).
2. member 리스너가 이 이벤트로 회원을 만든다. 이미 있으면 아무것도 하지 않는다.
3. 이메일 인증을 마치면 auth가 `AccountEmailVerified`를 내고, member가 인증 시각을 기록한다. 회원이 아직 없으면 실패하고 재시도 후 DLT로 간다.

- 이메일은 auth가 소유한다. member는 연락처 조회(notification)용으로 복사해 둔다. 이메일 변경은 아직 없다.
- 이벤트는 비동기라 가입 직후에는 회원이 없을 수 있다. 이때 `GET /v1/members/me`는 404 `MEMBER_NOT_FOUND`다. 화면은 잠시 뒤 다시 부른다.

## 규칙
- 이름은 50자까지. 연락처와 받는 사람 연락처는 하이픈으로 나눈 국내 전화번호(`010-1234-5678`, `02-123-4567`)다. 우편번호는 숫자 5자리다.
- 배송지는 10개까지다. 하나라도 있으면 기본 배송지가 꼭 하나다.
  - 첫 배송지는 기본이 된다. 기본으로 지정해 추가하거나 기본 지정 API를 부르면 바뀐다.
  - 기본 배송지를 지우면 남은 것 중 가장 최근에 추가한 배송지가 기본이 된다.
  - 기본 배송지는 배송지의 표시가 아니라 `member.default_address_id` 하나로 둔다. 둘이 될 수 없다.
- 배송지 변경은 회원 행을 잠그고(`SELECT ... FOR UPDATE`) 한다. 동시에 추가해도 10개를 넘지 않는다.
- 입력 규칙을 어기면 400 `MEMBER_INVALID_INPUT`이다. 오류 메시지에 입력값을 넣지 않는다.

## API
gateway 경로는 `/api/member` + 아래 경로다. 모두 사용자 토큰이 필요하고, 주체는 토큰의 `sub`다. 토큰 검증과 경로 규칙은 `common-security`가 한다([auth.md](auth.md) "인가").

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/v1/members/me` | 본인 프로필 |
| PUT | `/v1/members/me` | 이름, 연락처 변경. 연락처를 비우면 지운다 |
| POST | `/v1/members/me/withdraw` | 탈퇴. 아래 "탈퇴" |
| GET | `/v1/members/me/addresses` | 배송지 목록. 기본 배송지가 먼저, 나머지는 최근 순 |
| POST | `/v1/members/me/addresses` | 배송지 추가. `Idempotency-Key` 필수. 10개를 넘으면 422 `MEMBER_ADDRESS_LIMIT` |
| PUT | `/v1/members/me/addresses/{id}` | 배송지 수정 |
| POST | `/v1/members/me/addresses/{id}/default` | 기본 배송지 지정. 이미 기본이어도 204 |
| DELETE | `/v1/members/me/addresses/{id}` | 배송지 삭제. 이미 없어도 204 |

다른 회원의 배송지는 404 `MEMBER_ADDRESS_NOT_FOUND`다(삭제는 204이고 아무것도 지우지 않는다).

## 내부 API
서비스 간 조회다. `internal` scope 토큰만 부르고 gateway는 `/internal/**`을 넘기지 않는다.

| 메서드 | 경로 | 부르는 곳 | 설명 |
|---|---|---|---|
| GET | `/internal/v1/members/{memberId}/contact` | notification(알림 이메일) | `{email, emailVerified}`. 이름·전화번호는 주지 않는다. 없거나 탈퇴한 회원은 404 `MEMBER_NOT_FOUND`(가명 이메일을 주지 않는다) |

## 탈퇴 (ACC-4)
`POST /v1/members/me/withdraw`(204). 이미 탈퇴했어도 204다.
1. 판매자면 422 `MEMBER_SELLER_CANNOT_WITHDRAW`다(2026-10-18 결정). M5에서도 그대로 막는다. PRD에 폐점 요구사항이 없고, 미정산 금액이 남은 판매자를 정리하는 규칙이 필요해서다(2026-10-22 결정, [settlement.md](settlement.md) "범위 결정").
2. 진행 중인 주문·클레임이 있으면 422 `MEMBER_ONGOING_ORDERS`다. member가 order 내부 API로 확인한다([order.md](order.md) "내부 API"). 어떤 주문이 진행 중인지는 order가 정한다.
   - 호출 방식(OpenFeign, `client_credentials` 클라이언트 ID `member`, 타임아웃, 연결 실패만 한 번 재시도, 서킷 브레이커)은 order의 catalog 조회와 같다([order.md](order.md) "catalog 조회"). 값은 config-repo `member.yml`에 있다.
   - order를 부를 수 없으면(타임아웃, 5xx, 서킷 열림) 탈퇴하지 않고 503 `MEMBER_ORDER_UNAVAILABLE`이다. 확인하지 못한 채 탈퇴시키면 되돌릴 수 없기 때문이다.
3. 회원을 가명 처리한다. 이름은 `탈퇴회원`, 이메일은 `withdrawn-<id>@withdrawn.invalid`, 연락처는 비우고 배송지는 모두 지운다. 행은 남긴다(거래 기록이 memberId를 가리킨다).
4. 그 회원의 입점 신청은 모두 대표자·예금주·계좌 번호를 지운다. 심사 중이던 신청은 `CANCELLED`로 닫는다.
5. `MemberWithdrawn`을 낸다. auth가 계정을 비활성화하고 이메일을 가명으로 바꾸고 리프레시 토큰을 무효로 한다([auth.md](auth.md)). shop은 탈퇴 직후 로그아웃해서 BFF 세션과 auth 로그인 세션을 끝낸다. 같은 이메일로 바로 다시 가입할 수 있다(새 계정, 새 ID).

- 탈퇴한 회원은 없는 회원처럼 다룬다. 조회는 404 `MEMBER_NOT_FOUND`이고, 남은 액세스 토큰(최대 10분)으로 프로필·배송지를 바꿀 수 없다.
- 비밀번호 재확인은 하지 않는다. BFF 세션이 있어야 부를 수 있다. 화면(PR 9)에서 한 번 더 묻는다.

## 입점과 판매자 (SEL-1~3)
1. 회원이 사업자 정보(상호, 사업자 등록 번호, 대표자)와 정산 계좌(은행, 예금주, 계좌 번호)로 신청한다. 이메일 인증을 마쳐야 하고, 판매자이거나 심사 중인 신청이 있으면 409다.
2. 관리자가 승인하면 판매자(`Seller`, ID = memberId)가 생기고 `SellerApproved`가 나간다. auth가 `SELLER` 역할을 붙이고, 다음 토큰 갱신부터 판매자 앱에 들어갈 수 있다.
3. 반려에는 사유가 꼭 있어야 하고 `SellerRejected`가 나간다. 반려된 회원은 새로 신청할 수 있다.
4. 관리자가 정지하면(사유 필수) `SellerSuspended`가 나가고 auth가 `SELLER`를 뗀다. 상품 판매 중지는 catalog가 받는다([catalog.md](catalog.md) "판매 상태"). 해제하면 `SellerReinstated`가 나가고 역할이 돌아온다.

- 심사·정지·해제는 같은 결과로 다시 하면 아무것도 바꾸지 않고 204다(이벤트·감사 로그도 다시 남지 않는다). 이미 다른 결과로 심사한 신청은 409 `MEMBER_APPLICATION_ALREADY_REVIEWED`다.
- 동시성: 신청은 회원 행, 심사는 신청 행, 정지·해제는 판매자 행을 잠그고 한다. 회원당 심사 중인 신청 하나는 부분 유니크 인덱스로도 막는다.
- 사업자 등록 번호는 국세청 검증 숫자 규칙을 확인하고 `123-45-67890` 형식으로 저장한다.
- 계좌 번호는 숫자 10~16자리로 정리한 뒤 암호화한 값(`MEMBER_ACCOUNT_NUMBER_KEY`)과 마스킹한 값(`********6789`)만 저장한다. 응답에는 본인에게도 마스킹한 값만 준다. 원문은 정산 지급(M5)에서 복호화한다.
- 관리자 작업(승인, 반려, 정지, 해제)은 감사 로그(`audit_log`, [data.md](data.md))에 관리자 ID, 작업, 대상, 사유를 남긴다(NFR-8).
- 승인·반려는 notification이 인앱 알림과 메일로 알린다([notification.md](notification.md) "인앱 알림").

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/v1/members/me/seller-applications` | 입점 신청. `Idempotency-Key` 필수 |
| GET | `/v1/members/me/seller-applications` | 본인 신청 이력. 최근 순 |
| GET | `/v1/seller/me` | 판매자 본인 정보. `SELLER` |
| GET | `/v1/admin/seller-applications?status=&page=&size=` | 심사 목록(기본 `SUBMITTED`, 오래된 순). `ADMIN` |
| POST | `/v1/admin/seller-applications/{id}/approve` | 승인 |
| POST | `/v1/admin/seller-applications/{id}/reject` | 반려. 본문 `{"reason"}` |
| GET | `/v1/admin/sellers?status=&page=&size=` | 판매자 목록(최근 승인 순) |
| POST | `/v1/admin/sellers/{id}/suspend` | 정지. 본문 `{"reason"}` |
| POST | `/v1/admin/sellers/{id}/reinstate` | 정지 해제 |

관리자 목록은 쪽 번호(`page`는 0부터, `size`는 1~100) 응답 `{items, totalCount, page, size}`다.

## 개인정보 (NFR-7)
- 본인 프로필·배송지 응답은 원문이다. 본인이 고치려면 원래 값이 보여야 한다.
- 다른 사람이 보는 응답(관리자 심사 화면, 판매자의 주문 화면)과 로그에서는 `common-core`의 `Masking`으로 가린다. 관리자 심사·판매자 목록은 신청자 이메일, 대표자, 예금주를 가린다. 상호와 사업자 등록 번호는 공개 정보라 원문이다.
- 정산 계좌 번호는 본인에게도 마스킹한 값만 준다(원문을 저장하지 않는다).
- 요청·명령·조회 결과·엔티티의 `toString()`은 ID만 남긴다. 요청 로그에는 본문을 남기지 않는다(`common-web`).
