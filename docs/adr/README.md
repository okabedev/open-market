# 아키텍처 결정 기록 (ADR)

되돌리기 어렵거나 여러 서비스에 영향을 주는 결정을 남긴다. 결정을 바꿀 때는 기존 ADR을 고치지 않는다. 새 ADR을 쓰고, 기존 ADR의 상태를 `대체됨(→ NNNN)`으로 바꾼다.

| 번호 | 제목 | 상태 |
|---|---|---|
| [0001](0001-msa.md) | MSA와 Spring Cloud 인프라 | 승인 |
| [0002](0002-single-postgres-schema-per-service.md) | PostgreSQL 하나, 서비스별 스키마 | 승인 |
| [0003](0003-outbox-debezium.md) | Transactional Outbox + Debezium CDC | 승인 |
| [0004](0004-choreography-saga.md) | 코레오그래피 Saga | 승인 |
| [0005](0005-own-authorization-server.md) | 자체 OAuth2/OIDC 인가 서버 | 승인 |
| [0006](0006-nextjs-bff.md) | Next.js BFF와 앱 3개 | 승인 |
| [0007](0007-ddd-cqrs-packages.md) | DDD + CQRS 패키지 구조 | 승인 |
| [0008](0008-local-file-storage.md) | 로컬 file 서비스 | 승인 |
| [0009](0009-version-matrix.md) | 버전 조합 | 승인 |
| [0010](0010-code-style-tools.md) | 코드 스타일과 정적 분석 도구 | 승인 |
| [0011](0011-gateway-webflux.md) | Gateway는 Spring Cloud Gateway WebFlux | 승인 |
| [0012](0012-frontend-toolchain.md) | 프론트 도구와 버전 | 승인 |
| [0013](0013-auth-issuer-host.md) | auth issuer 고정 이름과 컨테이너의 host-gateway 접근 | 승인 |
| [0014](0014-bff-session.md) | BFF 세션은 openid-client와 Redis로 직접 만든다 | 승인 |
| [0015](0015-frontend-ui-stack.md) | 프론트 화면은 Tailwind CSS, shadcn/ui 방식 컴포넌트, Server Action 폼으로 만든다 | 승인 |
| [0016](0016-elasticsearch-nori.md) | Elasticsearch는 nori를 더한 커스텀 이미지, 한국어 필드는 mixed 분석기 하나로 색인·검색한다 | 승인 |
| [0017](0017-image-processing.md) | 썸네일은 JDK ImageIO + TwelveMonkeys 리더로 서브샘플링해 만든다 | 승인 |
| [0018](0018-toss-payments-integration.md) | 토스 결제는 주문서형 SDK v2와 서버 승인, 웹훅 대신 조회로 연동하고 E2E는 가짜 PG로 돌린다 | 승인 |
| [0019](0019-in-app-notification-polling.md) | 인앱 알림은 조회 방식으로 하고, 관리자 알림은 역할에 한 건만 둔다 | 승인 |
| [0020](0020-first-come-coupon-issuance.md) | 선착순 쿠폰은 Redis Lua 앞단 필터와 DB 조건부 발급·유니크로 지킨다 | 승인 |
| [0021](0021-order-reservation-join.md) | 재고·쿠폰 예약 결과는 주문에 따로 기록하고 주문 행 잠금으로 합류한다 | 승인 |


## 템플릿
```markdown
# NNNN. 제목
- 상태: 제안 | 승인 | 대체됨(→ NNNN)
- 날짜: YYYY-MM-DD

## 맥락
## 결정
## 결과
(좋아지는 점, 감수하는 점, 후속 작업)
## 검토한 대안
```
