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
| gateway | http://localhost:8080 | 외부 진입점. 예: `curl localhost:8080/api/member/v1/ping`(member 실행 필요). 관리 포트 8081은 호스트에 열지 않는다 |
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
- 서비스는 WSL IP로 Eureka에 등록된다. 그래서 컨테이너(Prometheus, gateway)가 그 주소로 접속할 수 있다.
- gateway 경유 호출: `curl localhost:8080/api/member/v1/ping`. 샘플 이벤트 발행은 `curl -X POST localhost:8080/api/member/v1/ping`이고, member 로그에 `샘플 이벤트를 처리했다`가 남는다(M1에서 지운다). 새로 띄운 서비스는 Eureka 등록과 gateway의 인스턴스 캐시 갱신 때문에 몇 초 뒤부터 라우팅된다. 그 전에는 503이다.
- gateway 자체를 IDE로 실행할 때는 컨테이너를 먼저 멈춘다(`docker compose stop gateway`). 호스트에서는 컨테이너 IP에 닿지 못하므로, 이때 뒤의 서비스도 IDE로 띄워야 한다.
- auth를 IDE로 실행할 때는 컨테이너를 먼저 멈춘다(`docker compose stop auth`). IDE auth도 호스트 포트 9000에서 들으므로 다른 컨테이너는 그대로 둔다.
- 로그인 없이 쓸 수 있는 것은 공개 경로뿐이다. 토큰이 필요한 API는 auth(M1) 이후 BFF를 거쳐 호출한다.
