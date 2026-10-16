# Open Market

여러 판매자가 입점하는 오픈 마켓을 **실제로 운영할 수 있는 수준**으로 만드는 풀스택 프로젝트입니다.
상품 등록부터 검색, 주문, 결제, 배송, 반품·환불, 쿠폰, 리뷰, 판매자 정산까지 전 과정을 다룹니다.

> 현재 단계: **M0 기반** — 설계 문서 작성 완료, 빌드·인프라 구성 중. 진행 상황은 [로드맵](docs/roadmap.md)에 있습니다.

## 기술 스택
| 영역 | 사용 기술 |
|---|---|
| 백엔드 | Java 25, Spring Boot 4, Spring Data JPA, QueryDSL, Spring Cloud (Gateway, Eureka, Config) |
| 인증 | Spring Authorization Server (OAuth2 / OIDC), Next.js BFF |
| 메시징 | Kafka, Transactional Outbox, Debezium CDC, 코레오그래피 Saga |
| 데이터 | PostgreSQL(서비스별 스키마), Redis, Elasticsearch(nori) |
| 프론트엔드 | Next.js (App Router), TypeScript, pnpm 모노레포 |
| 관측성 | OpenTelemetry, Prometheus, Grafana, Tempo, Loki |
| 결제 | 토스페이먼츠 (테스트 환경) |
| 실행 | Docker Compose (로컬, 비용 0) |

## 아키텍처
```mermaid
flowchart LR
  apps[shop · seller · admin<br/>Next.js BFF] --> gw[gateway]
  apps --> auth
  gw --> member & catalog & order & payment & fulfillment & promotion & review & settlement & search
  gw --> file & notification
  order & catalog & payment & fulfillment -- Outbox --> pg[(PostgreSQL)]
  pg -- Debezium --> kafka[(Kafka)]
  kafka --> order & catalog & payment & fulfillment & promotion & review & settlement & search & notification
```

| 구분 | 서비스 |
|---|---|
| core | member, catalog, order, payment, fulfillment, promotion, review, settlement, search |
| infra | gateway, discovery, config, auth, file, notification |
| clients | shop(구매자), seller(판매자), admin(관리자) |

자세한 설계는 [아키텍처 개요](docs/architecture/overview.md)와 [컨텍스트 맵](docs/architecture/context-map.md)에 있습니다.

## 설계에서 다루는 문제
- **분산 정합성**: 재고 예약 → 쿠폰 예약 → 결제 → 배송 생성으로 이어지는 주문 Saga와 보상 흐름 ([메시징과 Saga](docs/architecture/messaging.md))
- **이벤트 유실 방지**: DB 커밋과 이벤트 발행을 Outbox + CDC로 원자화, 멱등 소비자 ([ADR 0003](docs/adr/0003-outbox-debezium.md))
- **동시성**: 재고 초과 판매 방지, 선착순 쿠폰 초과 발급 방지
- **결제 경쟁 조건**: 결제 승인과 주문 만료가 겹칠 때 자동 보상 취소
- **보안**: 브라우저에 토큰을 두지 않는 BFF, 서비스 간 `client_credentials` ([인증·인가](docs/architecture/auth.md))
- **관측성**: Gateway부터 Kafka 소비자까지 이어지는 분산 트레이스 ([관측성](docs/architecture/observability.md))

## 문서
| 문서 | 내용 |
|---|---|
| [PRD](docs/prd.md) | 사용자 유형, 기능 요구사항, 비기능 요구사항 |
| [용어집](docs/glossary.md) | 도메인 용어와 코드 이름 |
| [로드맵](docs/roadmap.md) | 마일스톤 M0~M6과 완료 기준 |
| [아키텍처](docs/architecture/overview.md) | 서비스 구성, 통신, 데이터, 규약 |
| [ADR](docs/adr/README.md) | 주요 결정과 근거 |
| [개발 환경](docs/dev-setup.md) | 로컬 실행 준비 |

## 실행
M0에서 구성을 마치면 여기에 적습니다. 준비 사항은 [개발 환경](docs/dev-setup.md)을 보세요.

## 성능 개선 기록
k6 부하 테스트로 측정한 개선 내역입니다. (M2부터 기록)

| 대상 | 문제 | 개선 | 전 | 후 |
|---|---|---|---|---|
| | | | | |
