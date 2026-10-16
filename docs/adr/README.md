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
| [0008](0008-local-file-service.md) | 로컬 file 서비스 | 승인 |

버전 조합(Java, Spring Boot, Spring Cloud, 주요 라이브러리) ADR은 M0-b에서 0009로 쓴다.

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
