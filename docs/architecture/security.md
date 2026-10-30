# 보안 점검

NFR-6(마일스톤마다 OWASP Top 10 점검), NFR-7(개인정보), NFR-8(감사)을 점검한 결과를 둔다. 규칙 자체는 각 문서에 있다: 인증·인가와 보안 기본값은 [auth.md](auth.md), 개인정보 컬럼은 [data.md](data.md), 업로드 검사는 [file.md](file.md) "검사", 레이트 리밋은 [ADR 0011](../adr/0011-gateway-webflux.md).

## 점검 방법
- 코드를 읽어 항목마다 근거(파일과 줄)를 찾는다. 의심되는 것은 실패하는 테스트로 재현한 뒤 고친다.
- 고친 것은 회귀 테스트를 남긴다. 고치지 않은 것은 아래 "받아들인 위험"에 이유와 함께 적는다.

## M6 점검(2026-10-29)
OWASP Top 10(2021)과 2025판에 새로 들어온 항목(공급망, 예외 상황 처리)을 본다.

| 항목 | 결과 | 근거 |
|---|---|---|
| A01 접근 통제 | 고침 1 | 사용자·판매자 API는 토큰의 `sub`로 소유권을 거른다(order, fulfillment, review, settlement, catalog, promotion, payment, member, notification). 비공개 파일은 업로더·해당 판매자·관리자만 본다. 역할 규칙은 gateway와 `common-security`가 같은 순서로 검사한다. gateway가 서비스 OpenAPI 문서를 바깥에 넘겨 관리자·내부 API 모양이 드러났다 → 막았다 |
| A02 암호화 | 이상 없음 | config-repo에 비밀 값 기본값이 없다(`${X}`, compose는 `${X:?}`). 서명 키·TOTP 비밀 값·계좌 번호는 AES-256-GCM(무작위 IV)으로 저장한다. 이메일 인증 토큰은 256비트이고 SHA-256 해시만 저장한다 |
| A03 인젝션 | 이상 없음 | 네이티브 쿼리는 고정 문자열과 바인딩 변수다. Elasticsearch는 타입 있는 쿼리 빌더로 만든다. 파일 경로는 DB의 키로 만들고 저장 루트 밖을 거절한다. 클라이언트에 `dangerouslySetInnerHTML`이 없다. auth의 `th:utext`는 서버가 만든 QR SVG 하나다 |
| A04 안전하지 않은 설계 | 고침 2 | 이미지 압축 폭탄과 BFF의 클라이언트 주소 위조(아래 "고친 것") |
| A05 보안 설정 | 고침 1 | 앱 3개에 보안 헤더가 없어 관리자 화면을 다른 사이트 프레임에 넣을 수 있었다(클릭재킹) → 붙였다. Spring 쪽은 Security 기본 헤더를 쓰고, CORS를 열지 않으며, 처리하지 못한 예외는 일반 문구의 500이다. actuator는 `health`, `info`, `prometheus`만 연다 |
| A06 취약한 구성 요소 | 고침(2026-10-29) | 버전은 2주 넘은 것만 쓰고 ADR 0009 표에 고정한다. 자동 검사가 없었고, 믿었던 GitHub 기본 알림은 pnpm lockfile과 Gradle을 읽지 못했다 → clients는 `pnpm audit`, services는 의존성 그래프 제출과 Dependabot 알림으로 매주 본다([ADR 0026](../adr/0026-dependency-vulnerability-scan.md)). 처음 검사에서 Next.js 원격 코드 실행(critical) 등 10건을 찾아 올렸다. Gradle 쪽 첫 알림 47건(Tomcat 인증 우회 critical 등)은 BOM 버전을 덮어써 고쳤다(2026-10-30, ADR 0009 "보안 패치"). 컨테이너 이미지는 아래 "받아들인 위험" |
| A07 인증 실패 | 고침 3 | 로그인 레이트 리밋을 퍼센트 인코딩 경로로 피할 수 있었다. bcrypt 72바이트를 넘는 한글 비밀번호가 500이 됐다. BFF 세션이 토큰 갱신마다 늘어나 쓰는 동안 끝나지 않았다 → 모두 고쳤다. 계정 잠금(5회, 15분), TOTP 재사용 방지, 로그인·TOTP 뒤 세션 ID 교체, PKCE·nonce·state 쿠키는 이미 있다 |
| A08 무결성 | 고침 1 | 업로드 Route Handler가 Origin을 검사하지 않았다(문서는 검사한다고 적었다) → 고쳤다. 업로드는 내용의 매직 바이트로 형식을 정하고 변형을 다시 인코딩한다. CI 액션은 SHA로 고정하고, pnpm은 허용한 설치 스크립트만 돈다 |
| A09 로깅 | 고침 | 로그인 실패, 계정 잠금, TOTP 실패, 레이트 리밋 초과, gateway의 접근 거부가 로그에 남지 않았다 → 보안 이벤트 로그를 더했다([observability.md](observability.md) "보안 이벤트"). 감사 로그와 결제 원장을 서비스 계정이 고치고 지울 수 있었다 → 트리거로 막았다 |
| A10 SSRF | 이상 없음 | 사용자가 준 URL을 서버가 부르는 곳이 없다. BFF는 정해진 gateway 경로만, payment는 토스 고정 주소만 부른다 |
| 예외 상황 처리(2025) | 고침 | 이미지 처리에서 `OutOfMemoryError`가 날 수 있었다(A04 압축 폭탄) |

### 고친 것
| 결함 | 영향 | 고친 방법 |
|---|---|---|
| 좁고 긴 이미지(예: 1599x31000) 압축 폭탄 | 서브샘플링 배율을 폭으로만 정해 원본 해상도로 풀었다. 요청 하나가 힙(약 300MB)을 넘는 약 350MB를 쓴다. 로그인한 사용자 누구나 file을 멈출 수 있다 | 푸는 픽셀을 8백만 이하로 묶고, 긴 변이 짧은 변의 10배를 넘으면 `FILE_IMAGE_ASPECT_RATIO`로 거절한다(변형도 거대해지므로). [file.md](file.md) "검사", [ADR 0017](../adr/0017-image-processing.md) |
| BFF가 브라우저가 넣은 `X-Forwarded-For`를 믿음 | Next.js는 이 헤더가 이미 있으면 연결 주소를 붙이지 않는다. 값을 바꿔 가며 가입·이메일 인증 재발송의 IP 한도를 피할 수 있다(가입 남용, 메일 폭탄) | 앱을 `next start` 대신 앱 서버(`packages/bff/bin/serve.mjs`)로 띄워 `X-Forwarded-*`를 지우고 연결 주소를 넣는다. [ADR 0011](../adr/0011-gateway-webflux.md) |
| auth 로그인 레이트 리밋을 `/%6Cogin`으로 피함 | 필터는 디코딩 전 URI로, 폼 로그인은 디코딩한 경로로 맞춰서 한도 밖에서 로그인이 처리됐다(테스트로 재현). 한 IP에서 여러 계정에 비밀번호를 무제한 시도할 수 있다 | 필터도 폼 로그인과 같은 `PathPatternRequestMatcher`로 맞춘다 |
| 업로드 Route Handler에 Origin 검사가 없음 | 앱 3개는 같은 사이트(로컬 호스트, 운영은 형제 도메인)라 `SameSite=Lax`가 막지 못한다. 한 앱의 XSS로 다른 앱 세션으로 업로드할 수 있다 | `bff.isSameOrigin`을 로그아웃과 업로드가 같이 쓴다 |
| 앱에 보안 헤더 없음 | 관리자 버튼(상품 금지, 정산 지급)을 클릭재킹할 수 있다 | `frame-ancestors 'none'`, `X-Frame-Options`, `nosniff`, `Referrer-Policy`([auth.md](auth.md) "보안 기본값") |
| BFF 세션에 절대 수명이 없음 | 리프레시 토큰 rotation마다 수명이 다시 시작되고 세션 TTL도 다시 걸어, 관리자 세션(8시간)이 쓰는 동안 끝나지 않았다 | 수명을 로그인 시각부터 센다. 지나면 갱신하지 않고 세션을 지운다 |
| 한글 비밀번호가 bcrypt 72바이트를 넘음 | 64자 상한은 글자 수라 한글 24자에 영문·숫자를 더한 비밀번호(74바이트)도 통과했고, bcrypt가 예외("password cannot be more than 72 bytes")를 던져 가입이 500이 됐다 | UTF-8 72바이트 상한을 더해 400 `AUTH_INVALID_PASSWORD`로 거절한다 |
| gateway가 서비스 OpenAPI 문서를 넘김 | 로그인한 누구나 관리자·내부 API 모양을 볼 수 있다 | gateway가 `/api/*/v3/api-docs/**`를 막는다 |

### 감사·보안 로그(NFR-8, A09)
| 결함 | 영향 | 고친 방법 |
|---|---|---|
| 감사 로그와 결제 원장을 고치거나 지울 수 있음 | 서비스 계정이 Flyway와 앱을 같이 쓰는 스키마 소유자라 권한으로 막혀 있지 않았다. 앱 버그나 잘못 쓴 정리 작업이 기록을 바꿀 수 있다 | 트리거가 UPDATE·DELETE·TRUNCATE를 거절한다(`audit_log` `V0_7`, `payment_ledger_entry` payment `V6`). 앱은 소유자가 아닌 앱 계정(`<service>_app`)으로 연결해 트리거를 지우지 못한다(2026-10-29, [ADR 0027](../adr/0027-migration-and-app-db-accounts.md)) |
| 보안 이벤트가 로그에 없음 | 비밀번호 추측, 2단계 코드 추측, 권한 밖 호출을 알아챌 수 없었다 | auth와 gateway가 로거 `security`로 남긴다. 이메일 원문 대신 계정 ID와 IP를 쓴다 |
| 정산 즉시 실행의 감사가 확정과 따로 놂 | 요약 한 줄(대상 ID는 무작위 실행 ID)을 끝난 뒤에만 써서, 어느 정산이 관리자 실행으로 확정됐는지 알 수 없고 도중에 실패하면 이미 확정한 정산의 기록이 없었다 | 확정한 정산마다 같은 트랜잭션에 `SETTLEMENT_CONFIRMED`(대상 정산 ID, 실행 ID)를 쓰고, 요약은 실패해도 `finally`로 남긴다 |
| `audit_log` 보존 기간이 없음 | 정하지 않은 채로 남아 있었다 | 지우지 않는 것으로 정했다([data.md](data.md) "audit_log") |

- 시스템이 바꾸는 금액(결제 승인·실패, 보상 취소, 환불, 정기 정산)은 감사 로그 대신 결제 원장과 각 서비스의 행(`refund`, `settlement`, `settlement_item`)으로 누가(구매자·판매자·시스템), 언제, 무엇을 했는지 본다. 원장은 이제 추가만 된다.
- 남은 작은 것: Toss 호출 전에 실패한 환불(결제 없음, 남은 금액 초과)은 `refund` 행의 상태와 `RefundFailed` 이벤트로만 남는다. 관리자 재시도는 감사 로그에 이전 실패 코드를 남긴다.

### 개인정보(NFR-7)
로그와 API 응답에서 개인정보가 나가는 곳은 찾지 못했다. 본인 응답은 원문, 관리자 심사 화면은 마스킹, 정산 계좌 번호는 저장부터 마스킹이다([member.md](member.md) "개인정보"). 요청 로그는 본문을 남기지 않는다.

| 결함 | 영향 | 고친 방법 |
|---|---|---|
| `OrderPaid`에 배송지가 실림 | 받는 서비스(catalog, promotion, fulfillment, notification) 중 쓰는 곳이 없는데 outbox, Kafka, DLT에 이름·전화번호·주소가 남았다. 문서도 "fulfillment로 간다"와 "저장하지 않는다"로 서로 달랐다 | 이벤트에서 뺐다. 판매자는 order의 하위 주문 상세에서 받는다([context-map.md](context-map.md)) |
| 개인정보가 든 record가 기본 `toString()`을 씀 | 지금 로그에 남기는 곳은 없지만, 디버그 로그나 예외 메시지에 넣으면 원문이 나간다 | 주문 생성 명령·주문 상세의 배송지, 연락처 조회 결과, 가입 이벤트, 렌더링한 메일, 소비한 이벤트 봉투(`EventEnvelope`)가 ID만 남긴다. TOTP 비밀 값과 코드가 든 명령·결과도 같다 |
| member.md가 "판매자의 주문 화면"을 가린다고 적음 | 판매자 하위 주문 상세는 발송에 필요한 배송지를 원문으로 준다(PRD, [order.md](order.md)와 같다) | 문서를 고쳤다 |

- 빼기 전에 쌓인 `OrderPaid`는 outbox 행과 토픽 모두 7일 뒤 지워진다. DLT에서 수집한 행(`dead_letter`)은 처리가 끝나면 30일 뒤 지워지고, `PENDING`은 관리자가 처리할 때까지 남는다([messaging.md](messaging.md)).

### 받아들인 위험
| 위험 | 받아들이는 이유 |
|---|---|
| 개발용 Compose가 Redis(비밀번호 없음), Eureka, Config, Kafka, Postgres, Elasticsearch, Mailpit 포트를 모든 주소에 연다 | 로컬에서만 실행한다(비용 0 결정). 컨테이너가 `host-gateway`로 auth 포트를 부르므로 일괄로 `127.0.0.1`에 묶을 수 없다. gateway는 운영 배치에서 바깥에 열지 않는다(ADR 0011) |
| `next dev`는 브라우저가 넣은 `X-Forwarded-For`를 그대로 쓴다 | 개발 모드다. E2E·부하·장애 시험은 `pnpm start`(앱 서버)로 띄운다 |
| 세션 쿠키 `Secure`가 기본으로 꺼져 있다 | 로컬은 HTTP다. HTTPS로 서비스하면 `SESSION_COOKIE_SECURE=true`를 준다. HSTS도 그때 앞단에서 붙인다 |
| CSP는 `frame-ancestors`만 둔다 | 스크립트 출처 제한은 Next.js 인라인 스크립트에 nonce가 필요하고, 모든 페이지가 동적 렌더링이 된다. XSS 싱크는 위 A03처럼 없다 |
| `build-logic`의 `kotlin-gradle-plugin` 2.4.10 빌드 캐시 역직렬화 취약점(medium) | Gradle에 내장된 `kotlin-dsl`이 버전을 고르고 고친 버전은 베타뿐이다. convention 플러그인 컴파일에만 쓰고 원격 빌드 캐시가 없다. Gradle을 올릴 때 다시 본다(ADR 0009 "보안 패치") |
| 컨테이너 이미지 취약점 검사가 없다 | 로컬에서만 띄우고 버전은 ADR 0009 표로 관리한다. 라이브러리 검사는 [ADR 0026](../adr/0026-dependency-vulnerability-scan.md) |
| 계정 잠금으로 남의 계정을 15분 잠글 수 있다 | 잠금(ACC-1)은 비밀번호 추측을 막는 장치이고, IP 레이트 리밋이 한 곳에서의 반복을 줄인다 |
| 관리자 TOTP는 첫 로그인에 등록한다 | 비밀번호를 아는 사람이 먼저 등록할 수 있다. 시드 관리자는 처음 띄운 사람이 바로 등록한다([ADR 0024](../adr/0024-admin-totp-mfa.md)) |
| 로컬 실행에서는 앱 컨테이너가 마이그레이션 계정 비밀번호도 받는다 | 기동할 때 Flyway를 돌리기 위해서다. 앱 연결은 앱 계정이라 실행 중 코드는 DDL을 쓰지 못한다. 운영 배치에서는 마이그레이션 잡에만 준다([ADR 0027](../adr/0027-migration-and-app-db-accounts.md)) |
| AES-GCM에 연관 데이터(AAD)가 없다 | 다른 행의 암호문과 바꿔치려면 DB 쓰기 권한이 있어야 한다 |
| auth 관리자 API(DLT)는 토큰의 서명·만료만 본다 | auth 자신의 키만 받고 `ADMIN` 역할을 요구한다 |
| shop 결제 확인은 GET이다 | 토스가 돌려보내는 주소라 GET이어야 한다. 금액과 주문 소유자를 검사하고 멱등이다 |
| BFF 업로드는 본문을 다 받은 뒤 크기를 본다 | file의 multipart 한도(10MB)가 진짜 상한이다. BFF 메모리는 요청 하나에 10MB 안팎이다 |
