# infra (로컬 실행 환경)

비용 0 제약으로 모든 것을 Docker Compose로 로컬에서 실행한다. 구성 개요는 [overview.md](../docs/architecture/overview.md)의 "로컬 실행 구성"에 있다.

## 구조
```
infra/
├─ compose/        compose 파일. compose.yaml(기본, discovery·config·gateway·auth 포함), search.yaml, obs.yaml, apps.yaml(나머지 Spring 서비스)
├─ config-repo/    Spring Cloud Config native 저장소. application.yml(공통) + <service>.yml. config 컨테이너에 마운트된다
├─ postgres/init/  스키마, 서비스 계정, 복제 계정, publication과 outbox 자동 등록 트리거(서비스 목록 services.txt)
├─ debezium/       outbox 커넥터 설정과 등록 스크립트(connect 컨테이너가 기동할 때 실행). 커넥터는 하나
├─ kafka/          토픽 선언 topics.txt와 생성 스크립트(kafka-init 컨테이너가 실행). 브로커 자동 생성은 끈다
├─ observability/  OTel Collector, Tempo, Loki, Prometheus 설정
├─ grafana/        데이터 소스·대시보드 프로비저닝
├─ e2e/            마일스톤 E2E 실행 스크립트 run.sh(별도 Compose 프로젝트 + 앱 3개 + Playwright). 시나리오는 clients/e2e
├─ k6/             부하 테스트 시나리오 (M6)
└─ elasticsearch/  nori 플러그인 커스텀 이미지 (M2)
```

## 꼭 지킬 것
- 이미지 태그는 버전을 고정한다. `latest`를 쓰지 않는다. 태그를 바꾸면 [ADR 0009](../docs/adr/0009-version-matrix.md) 이미지 표도 고친다.
- 비밀 값은 레포 루트 `.env`에서 읽는다. compose 파일과 config-repo에 비밀 값을 직접 쓰지 않는다. config-repo에는 `${<NAME>_DB_PASSWORD}` 같은 자리 표시자만 두고, 서비스가 자기 환경 변수로 푼다.
- config-repo의 호스트 기본값(`localhost`)은 IDE·bootRun 기준이다. 컨테이너로 띄우는 서비스는 compose에서 `POSTGRES_HOST`, `EUREKA_URL` 같은 환경 변수로 바꾼다.
- 서비스를 추가하면 함께 고친다: `postgres/init`(스키마·계정), `kafka/topics.txt`, `config-repo/<service>.yml`, gateway 라우트(`config-repo/gateway.yml`). Debezium은 고치지 않는다(outbox 테이블은 트리거가 publication에 넣는다). 절차는 `add-service` skill에 있다.
- `context-map.md`에 토픽을 추가하면 `kafka/topics.txt`에도 `<토픽>`과 `<토픽>.DLT`를 더한다. 선언하지 않은 토픽으로 outbox에 쓰면 커넥터 태스크가 멈춘다.
- auth issuer(`auth.openmarket.local:9000`)에 닿아야 하는 컨테이너에는 `extra_hosts: auth.openmarket.local:host-gateway`를 준다. auth 컨테이너에는 별칭을 주지 않는다. auth가 컨테이너든 IDE든 호스트 포트 9000에 있다([ADR 0013](../docs/adr/0013-auth-issuer-host.md)).
- Spring 서비스 컨테이너에는 `mem_limit: 512m`을 둔다. 힙 비율(50%)은 `services/Dockerfile`에 있다. 상한 없이 띄우면 JVM이 VM 메모리 기준으로 힙을 잡는다. 근거와 실측은 [risks.md](../docs/architecture/risks.md) R6.

## 명령
레포 루트에서 실행한다. 루트 `compose.yaml`이 `infra/compose`의 파일들을 포함하고 루트 `.env`를 읽는다.
| 명령 | 용도 |
|---|---|
| `docker compose up -d --wait` | 기본 profile을 띄우고 healthy가 될 때까지 기다린다. 처음에는 discovery·config·gateway·auth 이미지를 빌드한다 |
| `docker compose up -d --wait --build discovery config gateway auth` | discovery·config·gateway·auth 코드를 바꾼 뒤 이미지를 다시 빌드한다. config-repo만 바꿨으면 빌드 없이 서비스만 재시작한다 |
| `docker compose --profile search --profile obs up -d --wait` | Elasticsearch와 관측성 스택까지 띄운다. 멈출 때도 같은 `--profile`을 붙인다 |
| `docker compose --profile apps up -d --wait --build` | 나머지 Spring 서비스까지 컨테이너로 띄운다(E2E). IDE로 띄울 서비스는 `docker compose stop <서비스>`로 멈춘다 |
| `infra/e2e/run.sh m1` | 마일스톤 완료 기준 E2E. 빈 볼륨의 별도 프로젝트(`openmarket-e2e`)를 띄워 돌리고 지운다. 개발용 스택과 앱은 먼저 멈춘다 |
| `docker compose ps` | 상태 확인 |
| `docker compose down` | 멈춤(데이터 유지) |
| `docker compose down -v` | 볼륨까지 지운다. `postgres/init`이나 `.env` 비밀번호를 바꾼 뒤 쓴다 |

포트와 접속 정보는 [dev-setup.md](../docs/dev-setup.md)에 있다.
