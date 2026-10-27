# 개발 환경 준비

기준 환경은 Windows + WSL2(Ubuntu)다. macOS나 Linux도 같은 도구를 쓰면 된다.
백엔드 빌드 명령은 [services/CLAUDE.md](../services/CLAUDE.md)에 있다.

## 1. 도구
| 도구 | 버전 | 비고 |
|---|---|---|
| JDK | 25 (Temurin) | `JAVA_HOME` 설정. Gradle은 래퍼로 실행하므로 따로 설치하지 않는다 |
| Node.js | 24 LTS | 레포 루트 `.nvmrc`. nvm 사용 시 `nvm use` |
| pnpm | 12.9.1 (`clients/package.json`의 `packageManager`) | `corepack enable`로 맞춘다. 버전 근거는 [ADR 0012](adr/0012-frontend-toolchain.md) |
| Docker | Docker Desktop(WSL 연동) 또는 WSL 안의 Docker Engine | Compose v2 |
| gh | 최신 | GitHub 작업용 |

## 2. WSL 메모리
전체 스택은 메모리를 많이 쓴다. Windows 사용자 폴더의 `.wslconfig`에 할당량을 적는다. Docker Desktop(WSL 연동)의 컨테이너도 이 할당량 안에서 돈다.

```ini
[wsl2]
memory=20GB
swap=8GB
processors=6
```

- `memory=20GB`는 전체 profile(`search`, `obs`, `apps`)을 띄우는 기준이다. M3 실측(서비스 11개)으로 VM 사용량은 약 14.4GB이고, 서비스 15개면 약 16GB로 본다. 실측과 계산은 [risks.md](architecture/risks.md)의 "메모리 예산"에 있다.
- `apps`를 띄운 채 전체 빌드(`./gradlew build`)를 하지 않는다. 빌드할 때는 `apps`를 내리거나 모듈 하나만 테스트한다(`./gradlew :core:<service>:test`). 16GB만 줄 수 있으면 `obs`도 함께 내린다.
- 그보다 적으면 `apps`와 `search`를 빼고 작업 중인 서비스만 IDE로 띄운다.
- Spring 서비스 컨테이너는 `mem_limit` 640MB, 힙은 그 40%(256MB)다(`services/Dockerfile`). file만 768MB다.

## 3. hosts
auth의 issuer 주소(`http://auth.openmarket.local:9000`)를 브라우저와 IDE가 쓰도록 이름을 등록한다. 컨테이너는 Compose의 `extra_hosts`가 처리하므로 할 것이 없다([ADR 0013](adr/0013-auth-issuer-host.md)).

```
127.0.0.1 auth.openmarket.local
```

- 브라우저(Windows): **Windows hosts 파일**(`C:\Windows\System32\drivers\etc\hosts`, 관리자 권한)에 추가한다.
- IDE·`bootRun`(WSL): WSL은 기본으로 Windows hosts에서 `/etc/hosts`를 만든다(`/etc/wsl.conf`의 `generateHosts`). Windows hosts에 추가한 뒤 PowerShell에서 `wsl --shutdown`으로 WSL을 다시 띄우면 반영된다. 바로 쓰려면 `echo "127.0.0.1 auth.openmarket.local" | sudo tee -a /etc/hosts`(다음 WSL 재시작 때 다시 만들어진다).
- 확인: `getent hosts auth.openmarket.local`, `curl -s http://auth.openmarket.local:9000/.well-known/openid-configuration`.
- IDE로 띄운 gateway는 Reactor Netty DNS 리졸버를 쓴다. 이 리졸버는 `/etc/hosts`만 읽으므로, 항목이 없으면 토큰 검증이 `Could not obtain the keys`(500)로 실패한다.

## 4. 환경 변수
```bash
cp .env.example .env
```
- auth 값(`AUTH_SIGNING_KEY_SECRET`, `*_CLIENT_SECRET`, `ADMIN_SEED_*`)은 예시 값 그대로 써도 된다. `AUTH_SIGNING_KEY_SECRET`을 바꾸면 저장된 서명 키를 읽지 못해 auth가 뜨지 않는다. 이때는 볼륨을 지우거나 `auth.signing_key` 행을 지운다.
- 토스페이먼츠 키(`TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`)는 `.env.example`의 문서용 공개 테스트 키를 그대로 쓴다. 주문서형 연동 키는 전자결제 신청(사업자 정보) 뒤에만 발급되기 때문이다([ADR 0018](adr/0018-toss-payments-integration.md)). 내 상점 키가 있으면 바꾼다. `test_`로 시작하는 키만 쓴다.
- `.env.example`에 새 변수가 생기면(예: M3의 `ORDER_CLIENT_SECRET`, `MEMBER_CLIENT_SECRET`) 이미 만든 `.env`에도 더한다. compose는 빠진 변수가 있으면 뜨지 않는다.
- `.env`는 커밋하지 않는다.

## 5. 실행
구성은 [overview.md](architecture/overview.md)의 "로컬 실행 구성"을 본다. 레포 루트에서 실행한다.

```bash
cp .env.example .env          # 처음 한 번
docker compose up -d --wait   # 기본 profile. 모두 healthy가 되면 끝난다
docker compose ps

# 필요할 때 profile을 더한다. 멈출 때도 같은 --profile을 붙여야 그 컨테이너까지 내려간다.
docker compose --profile search up -d --wait               # Elasticsearch
docker compose --profile obs up -d --wait                  # 관측성 스택
docker compose --profile apps up -d --wait --build         # Spring 서비스 전체를 컨테이너로(E2E). search가 쓰는 Elasticsearch도 함께 뜬다. 코드를 바꿨으면 --build
docker compose --profile search --profile obs --profile apps down
```

| 구성 요소 | 호스트 주소 | 비고 |
|---|---|---|
| PostgreSQL | `localhost:5432`, DB `openmarket` | 관리자 `postgres`, 서비스 계정 `<service>_app`. 비밀번호는 `.env` |
| Kafka | `localhost:9092` | 컨테이너끼리는 `kafka:29092` |
| Debezium Connect | http://localhost:8083 | 기동할 때 outbox 커넥터를 등록한다. 상태: `curl localhost:8083/connectors/outbox/status` |
| Redis | `localhost:6379` | |
| Mailpit | SMTP `localhost:1025`, 화면 http://localhost:8025 | |
| Kafka UI | http://localhost:8089 | |
| discovery (Eureka) | http://localhost:8761 | 등록된 인스턴스 화면. 레지스트리 API는 `/eureka/apps` |
| config | http://localhost:8888 | 서비스가 받을 설정 확인: `curl localhost:8888/member/default` |
| gateway | http://localhost:8080 | 외부 진입점. 토큰 없이 부를 수 있는 것은 가입·이메일 인증 API뿐이다(예는 6절). 관리 포트 8081은 호스트에 열지 않는다 |
| auth | http://auth.openmarket.local:9000 | 인가 서버. 로그인 화면 `/login`, discovery `/.well-known/openid-configuration`. hosts 항목이 필요하다(3절). 관리자 시드 계정은 `.env`의 `ADMIN_SEED_*`. 관리 포트 9001은 호스트에 열지 않는다 |
| Spring 서비스 (`apps`) | 호스트에 열지 않음 | gateway(8080)로 부른다. 목록은 `infra/compose/apps.yaml` |
| Elasticsearch (`search`, `apps`) | http://localhost:9200 | 인증 없음. nori 플러그인을 넣은 이미지를 처음 띄울 때 빌드한다. 상품 인덱스는 search 서비스가 뜰 때 만든다([search.md](architecture/search.md)) |
| OTel Collector (`obs`) | OTLP gRPC `localhost:4317`, HTTP `localhost:4318` | 서비스가 트레이스와 로그를 보낸다. `obs`를 띄우지 않으면 서비스 로그에 `Failed to export` ERROR가 가끔 남는다(OTel이 빈도를 제한한다). 무시해도 된다 |
| Prometheus (`obs`) | http://localhost:9090 | |
| Grafana (`obs`) | http://localhost:3003 | 로그인 없음. 데이터 소스 Prometheus, Tempo, Loki가 미리 등록된다 |

- `kafka-init`은 `infra/kafka/topics.txt`의 토픽을 만들고 끝나는 일회성 컨테이너다. `docker compose ps -a`에서 `Exited (0)`이면 정상이다. 실패하면 `docker compose logs kafka-init`로 원인을 본다.
- connect가 unhealthy면 outbox 커넥터 태스크가 RUNNING이 아니다. 원인은 커넥터 상태(`curl localhost:8083/connectors/outbox/status`)의 `trace`와 `docker compose logs connect`로 본다. 대처는 [messaging.md](architecture/messaging.md)의 "장애 때 동작"에 있다.
- 멈춤: `docker compose down`. 데이터는 볼륨에 남는다.
- 초기화: `docker compose down -v`. Postgres 초기화 스크립트(`infra/postgres/init`)는 빈 볼륨에서만 실행되므로, 스크립트나 `.env` 비밀번호를 바꾸면 볼륨을 지우고 다시 띄운다.
- 처음 `up`은 discovery, config, gateway, auth 이미지를 빌드하므로 몇 분 걸린다. 이후에는 캐시를 쓴다. 이 서비스들의 코드를 바꾸면 `docker compose up -d --wait --build <서비스>`로 다시 빌드한다. `apps` 서비스도 같다(예: `docker compose --profile apps up -d --wait --build member`).

## 6. 서비스 실행
인프라(기본 profile)를 띄운 뒤 `services/`에서 실행한다.

```bash
./gradlew :core:member:bootRun
```

- 설정은 config 서버에서 받는다(`infra/config-repo`). config-repo를 고쳤으면 서비스만 재시작한다.
- `apps` profile을 띄워 둔 상태라면 같은 서비스 컨테이너를 먼저 멈춘다(`docker compose stop member`). 둘 다 Eureka에 등록되면 gateway가 요청을 번갈아 보낸다.
- DB 비밀번호 같은 비밀 값은 레포 루트 `.env`에서 온다. `bootRun`은 `.env`를 환경 변수로 읽는다. IDE로 실행하면 실행 설정에 `.env` 파일을 환경 변수로 지정한다(IntelliJ: Run Configuration의 Environment variables 또는 EnvFile).
- file은 업로드 파일을 `FILE_STORAGE_ROOT`에 둔다. `bootRun`은 레포의 `infra/compose/data/file`(커밋하지 않는다)을 넣는다. IDE로 실행하면 실행 설정에 같은 경로를 `FILE_STORAGE_ROOT`로 더한다. 컨테이너는 볼륨 `file-data`(`/data`)다.
- 서비스는 WSL IP로 Eureka에 등록된다. 그래서 컨테이너(Prometheus, gateway)가 그 주소로 접속할 수 있다.
- gateway 경유 호출(가입): `curl -i -X POST localhost:8080/api/auth/v1/accounts -H 'Content-Type: application/json' -d '{"email":"buyer@example.com","password":"buyer-pass-1234","name":"구매자"}'`. 201이면 Mailpit(http://localhost:8025)에 인증 메일이 온다(notification 실행 필요). 가입·인증·재발송은 IP마다 1분에 5회까지다. 새로 띄운 서비스는 Eureka 등록과 gateway의 인스턴스 캐시 갱신 때문에 몇 초 뒤부터 라우팅된다. 그 전에는 503이다.
- gateway 자체를 IDE로 실행할 때는 컨테이너를 먼저 멈춘다(`docker compose stop gateway`). 호스트에서는 컨테이너 IP에 닿지 못하므로, 이때 뒤의 서비스도 IDE로 띄워야 한다.
- auth를 IDE로 실행할 때는 컨테이너를 먼저 멈춘다(`docker compose stop auth`). IDE auth도 호스트 포트 9000에서 들으므로 다른 컨테이너는 그대로 둔다.
- 로그인 없이 쓸 수 있는 것은 공개 경로뿐이다. 토큰이 필요한 API는 앱(BFF)으로 로그인해서 부른다(7절).

## 7. 앱 실행과 로그인
인프라(기본 profile, auth 포함)와 hosts 항목(3절)이 있어야 한다. `clients/`에서 실행한다.

```bash
pnpm --filter @openmarket/shop dev     # http://localhost:3000 (seller 3001, admin 3002)
```

- 앱 서버는 레포 루트 `.env`에서 클라이언트 시크릿(`SHOP_CLIENT_SECRET` 등)과 `REDIS_URL`(기본 `redis://localhost:6379`)을 읽는다.
- 화면의 "로그인"을 누르면 auth 로그인 화면(`auth.openmarket.local:9000/login`)으로 갔다가 돌아온다. 관리자 시드 계정(`.env`의 `ADMIN_SEED_*`)으로 세 앱 모두 로그인할 수 있다. admin 앱은 `ADMIN` 역할이 없으면 "이 앱을 쓸 권한이 없습니다."를 보여 준다.
- 가입 인증 메일은 notification이 보낸다. `./gradlew :infra:notification:bootRun`(또는 `apps` profile)을 띄우면 Mailpit(http://localhost:8025)에 도착한다. notification을 처음 띄우면 그 전에 쌓인 인증 메일도 한꺼번에 나간다.
- 화면: shop은 가입(`/signup`), 내 정보(`/my`), 배송지(`/my/addresses`), 입점 신청(`/my/seller`), 탈퇴(`/my/withdraw`). admin은 입점 심사(`/seller-applications`)와 판매자(`/sellers`). seller는 첫 화면에서 판매자 정보를 보여 주고, 판매자 역할이 없으면 안내와 "권한 다시 확인"(다시 로그인해서 새 역할을 받는다)을 보여 준다. 다른 앱 주소는 `SHOP_APP_URL`, `SELLER_APP_URL`(기본 localhost:3000, 3001)이다.
- 세션은 Redis 키 `bff:<앱>:session:*`에 있다. 브라우저에는 `om_sid_<앱>` 쿠키만 있다([ADR 0014](adr/0014-bff-session.md)).

## 8. E2E
마일스톤 완료 기준([roadmap.md](roadmap.md))을 Playwright로 확인한다. 시나리오는 `clients/e2e/tests/m<n>-*.spec.ts`다.

```bash
docker compose --profile apps --profile obs --profile search stop   # 개발용 스택을 멈춘다(데이터는 남는다)
infra/e2e/run.sh m2   # 마일스톤마다 m1, m2 …
```

- 별도 Compose 프로젝트(`openmarket-e2e`)를 빈 볼륨으로 띄우고(기본·`apps`·`e2e` profile), 앱 3개를 빌드해 `next start`로 띄운 뒤 시나리오를 돌린다. 끝나면 앱을 멈추고 프로젝트를 볼륨째 지운다. 남겨서 보려면 `E2E_KEEP=1 infra/e2e/run.sh m1`.
- 호스트 포트가 같아서 개발용 스택이나 `pnpm dev`가 떠 있으면 시작하지 않는다.
- 처음에는 서비스 이미지 빌드로 몇 분 걸린다. 그 뒤에는 시나리오까지 2~3분이다.
- Playwright 브라우저(Chromium)는 스크립트가 받는다. 브라우저가 쓰는 시스템 라이브러리(`libnspr4`, `libnss3` 등)는 처음 한 번 설치한다. `sudo`는 PATH를 바꿔 `corepack`을 찾지 못하므로, 필요한 패키지 목록만 뽑아 `apt`로 설치한다(2026-10-20 확인, Ubuntu 26.04 WSL).
  ```bash
  cd clients/e2e && ./node_modules/.bin/playwright install-deps --dry-run chromium   # 빠진 패키지 목록
  sudo apt-get update && sudo apt-get install -y <위 목록>
  ```
  라이브러리가 없으면 시나리오가 `error while loading shared libraries: libnspr4.so`로 바로 실패한다.
- 결과: 터미널 목록과 `clients/e2e/playwright-report`(HTML). 실패한 시나리오는 `clients/e2e/test-results`에 trace와 스크린샷이 남는다(`corepack pnpm --filter @openmarket/e2e exec playwright show-trace <파일>`). 앱 로그는 `infra/e2e/.out`.
- CI에서는 돌리지 않는다(2026-10-18 결정). 마일스톤 마감과 큰 변경 뒤에 돌린다.

- 시간 설정: `run.sh`가 결제 기한(`ORDER_PAYMENT_TIMEOUT=40s`), 만료 작업 주기(`ORDER_EXPIRY_INTERVAL=2s`), 보상 취소 주기(`PAYMENT_CANCEL_INTERVAL=2s`), 환불 주기(`PAYMENT_REFUND_INTERVAL=2s`), 배송 시뮬레이터 주기와 지연(`FULFILLMENT_SIMULATOR_INTERVAL=2s`, `FULFILLMENT_IN_TRANSIT_AFTER=3s`, `FULFILLMENT_DELIVER_AFTER=3s`), 자동 구매확정 주기와 기한(`FULFILLMENT_CONFIRM_INTERVAL=2s`, `FULFILLMENT_AUTO_CONFIRM_AFTER=30s`), 정산 기간과 마감 지연(`SETTLEMENT_PERIOD_LENGTH=1m`, `SETTLEMENT_CLOSE_DELAY=5s`)을 줄이고, 가입·이메일 인증(`GATEWAY_STRICT_RATE_LIMIT=60`, 기본 1분에 5회)과 로그인(`AUTH_LOGIN_RATE_LIMIT=100`, 기본 1분에 10회)의 IP당 한도를 늘린다. 시나리오가 한 IP에서 계정을 1분 안에 여럿 만들고 로그인하기 때문이다. config-repo는 `${ORDER_PAYMENT_TIMEOUT:15m}`처럼 기본값을 갖고, `apps.yaml`은 값이 있을 때만 컨테이너에 넘긴다. 시나리오는 `clients/e2e/support/env.ts`의 `paymentTimeoutMs`로 같은 값을 읽는다.

### 가짜 PG
E2E는 토스페이먼츠 대신 가짜 PG(`infra/fake-pg`)로 결제한다([ADR 0018](adr/0018-toss-payments-integration.md)). `run.sh`가 `e2e` profile로 띄우고(호스트 포트 8090), 앱 코드는 그대로 두고 주소만 바꾼다.
- payment: `TOSS_API_URL=http://fake-pg:8090`(Compose 네트워크). 승인 `POST /v1/payments/confirm`, 조회 `GET /v1/payments/{paymentKey}`, 취소 `POST /v1/payments/{paymentKey}/cancel`을 흉내 낸다. `Idempotency-Key`는 토스처럼 같은 키면 같은 응답, 처리 중이면 409 `IDEMPOTENT_REQUEST_PROCESSING`이다.
- shop: `TOSS_SDK_URL=http://localhost:8090/v2/standard`. `loadTossPayments(clientKey, { src })`가 가짜 SDK 스크립트를 불러온다. 결제하기를 누르면 가짜 PG의 결제 인증 화면(`/checkout`)으로 가고, "결제 승인"은 `successUrl`, "결제 취소"는 `failUrl`(`PAY_PROCESS_CANCELED`)로 돌려보낸다.
- 제어 API(시나리오용, 인증 없음)

  | 요청 | 하는 일 |
  |---|---|
  | `PUT /control/orders/{orderId}` `{"confirmDelayMs": 15000}` | 그 주문의 승인 응답을 늦춘다. 승인은 요청을 받은 때 끝나므로(조회하면 `DONE`) 만료와 승인의 경합, 응답 타임아웃을 재현한다 |
  | `PUT /control/orders/{orderId}` `{"confirmError": {"status": 403, "code": "REJECT_CARD_PAYMENT", "message": "…"}}` | 그 주문의 승인을 그 오류로 거절한다(결제는 `ABORTED`) |
  | `PUT /control/orders/{orderId}` `{"cancelError": {"status": 403, "code": "NOT_CANCELABLE_PAYMENT", "message": "…"}}` | 그 주문의 취소·부분취소를 그 오류로 거절한다. 관리자 재시도(환불, 보상 취소)를 재현한다. `{}`로 다시 보내면 설정이 지워진다 |
  | `GET /control/orders/{orderId}` | 그 주문의 결제 객체 목록과 승인·취소 요청 수(`confirmRequests`, `cancelRequests`) |
  | `DELETE /control` | 결제와 설정을 모두 지운다 |
- 상태는 메모리에만 있다. 컨테이너를 다시 띄우면 지워진다.
- 개발용 스택에서 손으로 띄우려면 `TOSS_API_URL=http://fake-pg:8090 docker compose --profile apps --profile e2e up -d --wait fake-pg payment`, shop은 `TOSS_SDK_URL=http://localhost:8090/v2/standard pnpm --filter @openmarket/shop dev`다. 실제 토스로 돌아가려면 변수 없이 payment를 다시 띄운다.
- 가짜 PG 테스트: 레포 루트에서 `node --test infra/fake-pg/*.test.mjs`(CI `compose` 잡).

## 9. 부하 시험
NFR-9(조회·검색 p95 200ms, 주문 생성 p95 500ms, 선착순 쿠폰 초과 발급 0건)를 k6로 잰다. 시나리오는 `infra/k6/*.js`, 결과 기록은 [risks.md](architecture/risks.md) "부하 측정"이다.

```bash
docker compose --profile apps --profile obs --profile search stop   # 개발용 스택을 멈춘다(포트가 겹친다)
infra/k6/run.sh                  # browse, order, coupon을 차례로
infra/k6/run.sh browse           # 하나만
BROWSE_RATE=300 infra/k6/run.sh browse   # 부하 크기를 바꿔서
```

- 별도 Compose 프로젝트(`openmarket-load`)를 빈 볼륨으로 띄우고(기본·`apps`·`search`·`obs` profile) 시나리오마다 k6 컨테이너(`load` profile, `infra/compose/load.yaml`)를 돌린 뒤 지운다. 남겨서 보려면 `LOAD_KEEP=1`, 남긴 스택에 다시 돌리려면 `LOAD_REUSE=1`이다. 같은 시나리오를 조건을 바꿔 여러 번 돌릴 때는 `LOAD_LABEL=<라벨>`로 결과 파일 이름을 나눈다.
- 시나리오는 `setup()`에서 데이터를 API로 만든다(판매자 가입·이메일 인증·입점 승인, 이미지를 올린 상품, 구매자). 기동 직후에는 JIT 예열로 지연이 크므로 30초 예열 단계(`WARMUP_DURATION`)를 먼저 돌리고, 임계값은 측정 단계에만 건다.

  | 시나리오 | 부하(기본값) | 바꾸는 변수 |
  |---|---|---|
  | `browse` | 비로그인 상품 상세 3 : 검색 1, 초당 100건, 2분 | `BROWSE_RATE`, `BROWSE_DURATION` |
  | `order` | 이메일 인증한 구매자 50명이 주문 생성(결제 없음), 초당 10건, 2분 | `ORDER_RATE`, `ORDER_DURATION`, `ORDER_BUYERS` |
  | `coupon` | 수량 100장 플랫폼 쿠폰에 구매자 1,000명이 한꺼번에 받기 | `COUPON_BUYERS`, `COUPON_QUANTITY` |
- k6는 BFF 자리에서 gateway(`http://gateway:8080`)를 부른다. 요청마다 `X-Forwarded-For`를 바꿔 여러 사용자의 요청처럼 보낸다. gateway는 그 마지막 값으로 익명 레이트 리밋 키를 정한다(R13). auth 로그인 한도는 실제 접속 주소를 보므로 `run.sh`가 `AUTH_LOGIN_RATE_LIMIT`을 늘린다.
- 결과: `infra/k6/.out/<이름>.log`(k6 출력), `-summary.json`(지표), `-stats.csv`(5초마다 컨테이너 CPU·메모리, k6 포함), `-memory.txt`(시나리오 뒤 cgroup `anon`·`peak`·상한 도달), `-pool.txt`(Prometheus의 서비스별 연결 풀 대기·사용 최대값).
- k6가 임계값을 넘으면 종료 코드가 0이 아니고 `run.sh`도 실패로 끝난다. 남은 시나리오는 계속 돈다.

## 10. 장애 주입
장애 중에도 주문·결제 Saga가 끝 상태로 가고 데이터가 맞는지 본다(R16, NFR-1·4). 결과 기록은 [risks.md](architecture/risks.md) "장애 주입"이다.

```bash
docker compose --profile apps --profile obs --profile search stop   # 개발용 스택을 멈춘다(포트가 겹친다)
infra/fault/run.sh               # kafka, db, db-down, pg를 차례로
infra/fault/run.sh kafka         # 하나만
FAULT_AT=30 FAULT_FOR=120 infra/fault/run.sh kafka   # 장애 시작 시점과 길이(초)
```

- 별도 Compose 프로젝트(`openmarket-fault`)를 빈 볼륨으로 띄운다(기본·`apps`·`e2e`(가짜 PG)·`fault`(Toxiproxy) profile). 서비스의 DB 연결은 Toxiproxy를 거친다(`SERVICE_POSTGRES_HOST=toxiproxy`). Debezium은 Postgres에 바로 붙는다. 결제 기한은 4분, 만료 작업은 10초마다로 줄인다.
- 장애마다 k6 `infra/k6/saga.js`가 주문 생성 → 결제 준비 대기 → 가짜 PG 승인 → 결제 승인을 초당 2건, 3분 동안 흘린다(`SAGA_RATE`, `SAGA_DURATION`, `SAGA_BUYERS`). 실패하면 같은 멱등 키로 다시 부른다.

  | 장애 | 넣는 방법 | 기본 |
  |---|---|---|
  | `kafka` | 브로커 컨테이너를 멈췄다가 다시 띄운다 | 시작 60초 뒤 60초 |
  | `db` | Toxiproxy `latency` toxic(서비스 → Postgres 응답 지연) | 300ms ± 100ms(`FAULT_DB_LATENCY_MS`), 60초 |
  | `db-down` | Toxiproxy 프록시를 꺼서 연결을 끊는다 | 60초 |
  | `pg` | 세 번째 주문마다 가짜 PG가 승인 응답을 15초 늦게 준다(payment 읽기 타임아웃 10초) | 내내 |
- k6가 끝나면 끝나지 않은 주문·결제가 없어질 때까지(최대 10분) 기다린 뒤 `infra/fault/check.sql`(슈퍼유저)과 `.DLT` 토픽 메시지 수를 센다. 검사는 주문 끝 상태, 결제·주문·재고 예약의 짝, 남은 재고 예약, SKU별 재고 차감이다.
- 결과: `infra/fault/.out/<장애>.log`(k6 출력과 막힌 단계 수), `<장애>-check.txt`(검사). 위반이나 DLT 메시지가 있으면 종료 코드가 0이 아니다. 남겨서 보려면 `FAULT_KEEP=1`이다.
