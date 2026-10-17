# Open Market

여러 판매자가 입점하는 오픈 마켓. 풀스택 포트폴리오이며 목표는 **실제 운영 가능한 수준**이다.
요구사항은 [docs/prd.md](docs/prd.md), 진행 상황은 [docs/roadmap.md](docs/roadmap.md)에 있다.

## 소통
- 사용자와는 한국어로 소통한다. 문서와 커밋 메시지도 한국어로 쓴다. 코드 식별자와 파일 이름은 영어로 쓴다.
- 일정은 기한 없는 마일스톤 `M0`~`M6`으로 부른다. 주차나 스프린트로 말하지 않는다.
- 아직 정하지 않은 명령, 버전, 설정은 지어내지 않는다. 문서에 "M<n>에서 확정"이라고 적혀 있으면 그 마일스톤에서 정하고 문서를 고친다.

## 바꾸지 않는 결정
사용자가 정한 것이다. 바꾸자고 제안할 때는 이유를 들어 먼저 묻는다. 근거는 [docs/adr](docs/adr/README.md)에 있다.
- MSA. Spring Cloud Gateway, Eureka, Config를 쓴다.
- 백엔드: Java 25, Spring Boot 4, Spring Data JPA, QueryDSL, Gradle 멀티 모듈.
- 내부 설계: DDD + CQRS. 패키지는 `command/{application,domain}`, `query/{application,dao,dto}`, `infra`, `ui`.
- 프론트: Next.js(App Router) + TypeScript, 앱 3개(shop, seller, admin), pnpm 모노레포. Next.js 서버가 BFF다.
- 데이터: PostgreSQL 하나에 서비스별 스키마와 계정을 둔다. 다른 스키마는 읽지 않는다.
- 메시징: Kafka. Transactional Outbox + Debezium CDC로 발행하고, 코레오그래피 Saga로 정합성을 맞춘다. 소비자는 멱등이다.
- 인증: 자체 `auth` 서버(Spring Authorization Server, OAuth2/OIDC).
- 비용 0. Docker Compose로 로컬에서만 실행한다. 클라우드 서비스는 쓰지 않는다. 파일은 로컬 `file` 서비스가 저장한다.
- 외부 연동: 토스페이먼츠 테스트 결제, Elasticsearch(nori), OTel + Prometheus/Grafana/Tempo/Loki.

## 서비스
이름은 고유 명사 한 단어다. `-service` 같은 접미사를 붙이지 않는다. 자세한 책임과 포트는 [overview.md](docs/architecture/overview.md)에 있다.
- core: member, catalog, order, payment, fulfillment, promotion, review, settlement, search
- infra: gateway, discovery, config, auth, file, notification
- clients: shop, seller, admin

## 디렉터리
```
docs/        PRD, 용어집, 로드맵, 개발 환경, architecture/, adr/
services/    Gradle 멀티 모듈: build-logic/, libs/, core/<service>, infra/<service>   → services/CLAUDE.md
clients/     pnpm 모노레포: apps/{shop,seller,admin}, packages/                      → clients/CLAUDE.md
infra/       compose/, config-repo/, postgres/, debezium/, kafka/, elasticsearch/, observability/, grafana/, k6/  → infra/CLAUDE.md
.claude/     skills/
```

## 핵심 규칙
1. **용어**: [glossary.md](docs/glossary.md)의 이름을 쓴다. 새 개념은 용어집에 먼저 추가한다.
2. **이벤트**: 서비스 사이 상태 변경은 outbox 이벤트로만 전파한다. 동기 호출(OpenFeign)은 조회에만 쓴다. 이벤트를 추가하거나 바꾸면 [context-map.md](docs/architecture/context-map.md)를 먼저 고친다.
3. **스키마**: Flyway로만 바꾼다. 적용된 마이그레이션은 고치지 않는다.
4. **멱등**: 리스너, 배치, 중복되면 안 되는 상태 변경 API는 다시 실행해도 결과가 같아야 한다.
5. **보안**: 브라우저에 토큰을 두지 않는다. 비밀 값은 `.env`에만 두고 커밋하지 않는다. 로그에 개인정보를 남기지 않는다.
6. **테스트 먼저**: 기능은 실패하는 테스트부터 쓴다. 의존 방향은 ArchUnit이 검사한다.
7. **문서 정합성**: 코드가 문서와 달라지면 같은 PR에서 문서를 고친다. 정보는 한 곳에만 쓰고 나머지는 링크한다.

## Git
- 기본 브랜치는 `main`이다. 작업은 `<type>/<짧은-설명>` 브랜치에서 하고 PR로 합친다(squash merge).
- 커밋은 Conventional Commits 형식에 한국어 설명을 붙인다. 예: `feat(order): 주문 만료 스케줄러 추가`. type은 `feat`, `fix`, `docs`, `refactor`, `test`, `build`, `ci`, `chore`다. scope는 서비스 이름, `clients`, `infra`, `libs`다.
- push, PR 생성, merge, 태그는 사용자에게 확인받고 한다.
- CI는 `.github/workflows/ci.yml`이다. 바뀐 경로(백엔드, clients, Compose)만 검사하고, 결과를 모은 `ci` 체크가 merge 조건이다.
- 저장소 설정(squash만 허용, 라벨, `main` 보호)은 `.github/`의 선언 파일이 원본이다. 바꾸면 `.github/apply-settings.sh`로 적용한다. 이슈·PR은 `.github/` 템플릿을 쓴다.

## 작업 방식
- 마일스톤 시작·마감: `milestone` skill
- 서비스 추가: `add-service` skill
- 유스케이스(커맨드·쿼리) 추가: `add-usecase` skill
- 마일스톤 마감 전, 큰 변경 후 점검: `design-review` skill
- 기술 리스크는 [risks.md](docs/architecture/risks.md)에서 상태를 갱신한다. 결정이 생기면 ADR을 쓴다.

## 명령
- 백엔드: `services/`에서 `./gradlew build` (포맷 고치기 `./gradlew spotlessApply`). 자세한 것은 [services/CLAUDE.md](services/CLAUDE.md).
- 로컬 인프라: 레포 루트에서 `cp .env.example .env`(처음 한 번), `docker compose up -d --wait`(discovery·config·gateway 포함). 검색·관측성은 `--profile search --profile obs`, 서비스 전체 컨테이너(E2E)는 `--profile apps`를 더한다. 포트와 초기화는 [dev-setup.md](docs/dev-setup.md).
- 서비스 실행: `services/`에서 `./gradlew :core:<service>:bootRun`. 루트 `.env`를 환경 변수로 읽는다.
- 프론트: `clients/`에서 `pnpm install`, `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build`(pnpm 버전은 corepack이 맞춘다). 앱 하나만 띄우려면 `pnpm --filter @openmarket/shop dev`. 자세한 것은 [clients/CLAUDE.md](clients/CLAUDE.md).
