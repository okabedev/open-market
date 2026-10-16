# infra (로컬 실행 환경)

비용 0 제약으로 모든 것을 Docker Compose로 로컬에서 실행한다. 구성 개요는 [overview.md](../docs/architecture/overview.md)의 "로컬 실행 구성"에 있다.

## 구조 (M0-c에서 만든다)
```
infra/
├─ compose/        compose 파일. profile: (기본), search, obs, apps
├─ config-repo/    Spring Cloud Config native 저장소. application.yml + <service>.yml
├─ postgres/init/  스키마, 서비스 계정, 복제 계정, publication 생성 스크립트(서비스 목록 services.txt)
├─ debezium/       커넥터 설정과 등록 스크립트 (커넥터는 하나)
├─ kafka/topics    토픽 선언 (자동 생성 끔)
├─ grafana/        데이터 소스·대시보드 프로비저닝
├─ k6/             부하 테스트 시나리오 (M6)
└─ elasticsearch/  nori 플러그인 커스텀 이미지 (M2)
```

## 꼭 지킬 것
- 이미지 태그는 버전을 고정한다. `latest`를 쓰지 않는다.
- 비밀 값은 레포 루트 `.env`에서 읽는다. compose 파일과 config-repo에 비밀 값을 직접 쓰지 않는다.
- 서비스를 추가하면 함께 고친다: `postgres/init`(스키마·계정), `debezium`(`table.include.list`, publication), `kafka/topics`, `config-repo/<service>.yml`, gateway 라우트. 절차는 `add-service` skill에 있다.
- auth 컨테이너에는 네트워크 별칭 `auth.openmarket.local`을 준다([auth.md](../docs/architecture/auth.md)).
- 메모리 상한(`mem_limit`)과 JVM 힙 비율은 R6 실측 결과에 맞춘다([risks.md](../docs/architecture/risks.md)).

## 명령
레포 루트에서 실행한다. 루트 `compose.yaml`이 `infra/compose/compose.yaml`을 포함하고 루트 `.env`를 읽는다.
| 명령 | 용도 |
|---|---|
| `docker compose up -d --wait` | 기본 profile을 띄우고 healthy가 될 때까지 기다린다 |
| `docker compose ps` | 상태 확인 |
| `docker compose down` | 멈춤(데이터 유지) |
| `docker compose down -v` | 볼륨까지 지운다. `postgres/init`이나 `.env` 비밀번호를 바꾼 뒤 쓴다 |

포트와 접속 정보는 [dev-setup.md](../docs/dev-setup.md)에 있다.
