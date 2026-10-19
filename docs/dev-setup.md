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

- `memory=20GB`는 전체 profile(`search`, `obs`, `apps`)을 띄운 채 `./gradlew build`까지 돌리는 기준이다. 서비스 15개일 때 VM 사용량은 약 11GB, 빌드 중에는 약 16~18GB로 계산했다. 실측과 계산은 [risks.md](architecture/risks.md)의 "메모리 예산"에 있다.
- 16GB만 줄 수 있으면 `apps`를 띄운 채 전체 빌드를 하지 않는다. 빌드할 때는 `apps`를 내리거나 모듈 하나만 테스트한다(`./gradlew :core:<service>:test`).
- 그보다 적으면 `apps`와 `search`를 빼고 작업 중인 서비스만 IDE로 띄운다.
- Spring 서비스 컨테이너는 `mem_limit` 512MB, 힙은 그 50%다(`services/Dockerfile`).

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
- 토스페이먼츠 테스트 키는 [토스페이먼츠 개발자센터](https://developers.tosspayments.com)에서 발급받아 `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`에 넣는다. `test_`로 시작하는 키만 쓴다.
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
docker compose --profile apps up -d --wait --build         # Spring 서비스 전체를 컨테이너로(E2E). 코드를 바꿨으면 --build
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
| Elasticsearch (`search`) | http://localhost:9200 | 인증 없음. nori 플러그인은 M2 |
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
infra/e2e/run.sh m1
```

- 별도 Compose 프로젝트(`openmarket-e2e`)를 빈 볼륨으로 띄우고(기본·`apps` profile), 앱 3개를 빌드해 `next start`로 띄운 뒤 시나리오를 돌린다. 끝나면 앱을 멈추고 프로젝트를 볼륨째 지운다. 남겨서 보려면 `E2E_KEEP=1 infra/e2e/run.sh m1`.
- 호스트 포트가 같아서 개발용 스택이나 `pnpm dev`가 떠 있으면 시작하지 않는다.
- 처음에는 서비스 이미지 빌드로 몇 분 걸린다. 그 뒤에는 시나리오까지 2~3분이다.
- Playwright 브라우저(Chromium)는 스크립트가 받는다. 브라우저가 쓰는 시스템 라이브러리는 처음 한 번 설치한다: `cd clients && sudo corepack pnpm --filter @openmarket/e2e exec playwright install-deps chromium`.
- 결과: 터미널 목록과 `clients/e2e/playwright-report`(HTML). 실패한 시나리오는 `clients/e2e/test-results`에 trace와 스크린샷이 남는다(`corepack pnpm --filter @openmarket/e2e exec playwright show-trace <파일>`). 앱 로그는 `infra/e2e/.out`.
- CI에서는 돌리지 않는다(2026-10-18 결정). 마일스톤 마감과 큰 변경 뒤에 돌린다.
