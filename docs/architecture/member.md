# 회원 (member 서비스)

회원 프로필과 배송지(ACC-3)를 갖는다. 입점 신청·심사(SEL-1~3)와 탈퇴(ACC-4)는 [roadmap.md](../roadmap.md) M1의 다음 작업에서 더한다.
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
| GET | `/v1/members/me/addresses` | 배송지 목록. 기본 배송지가 먼저, 나머지는 최근 순 |
| POST | `/v1/members/me/addresses` | 배송지 추가. `Idempotency-Key` 필수. 10개를 넘으면 422 `MEMBER_ADDRESS_LIMIT` |
| PUT | `/v1/members/me/addresses/{id}` | 배송지 수정 |
| POST | `/v1/members/me/addresses/{id}/default` | 기본 배송지 지정. 이미 기본이어도 204 |
| DELETE | `/v1/members/me/addresses/{id}` | 배송지 삭제. 이미 없어도 204 |

다른 회원의 배송지는 404 `MEMBER_ADDRESS_NOT_FOUND`다(삭제는 204이고 아무것도 지우지 않는다).

## 개인정보 (NFR-7)
- 본인 프로필·배송지 응답은 원문이다. 본인이 고치려면 원래 값이 보여야 한다.
- 다른 사람이 보는 응답(관리자 심사 화면, 판매자의 주문 화면)과 로그에서는 `common-core`의 `Masking`으로 가린다.
- 요청·명령·조회 결과·엔티티의 `toString()`은 ID만 남긴다. 요청 로그에는 본문을 남기지 않는다(`common-web`).
