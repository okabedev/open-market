---
name: add-usecase
description: 오픈 마켓 백엔드 서비스에 커맨드 유스케이스(상태 변경) 또는 쿼리(조회) 하나를 추가할 때 사용한다. 테스트를 먼저 쓰고 DDD+CQRS 패키지 규약과 이벤트 규칙을 지킨다.
---

# 유스케이스 추가

규약 원본: [conventions.md](../../../docs/architecture/conventions.md). 용어: [glossary.md](../../../docs/glossary.md).

## 0. 준비
- 이 유스케이스가 PRD의 어느 ID(예: ORD-2)를 구현하는지 확인한다.
- 새 용어가 있으면 용어집에 먼저 추가한다.
- 이벤트를 새로 발행하거나 구독하면 [context-map.md](../../../docs/architecture/context-map.md)를 먼저 고친다.

## 커맨드 (상태 변경)
1. **도메인 테스트부터** 쓴다. 집계의 상태 전이와 불변식이 실패하는 테스트를 `command.domain` 기준으로 작성한다.
2. 집계, 값 객체, 도메인 이벤트를 구현해 테스트를 통과시킨다. 도메인은 Spring에 의존하지 않는다.
3. **유스케이스 테스트**: 포트와 저장소는 가짜 구현으로 두고, 결과와 outbox에 기록된 이벤트를 검증한다.
4. `<동사><명사>Command`(record), `<동사><명사>UseCase`(`execute` 하나)를 만든다. 트랜잭션 경계는 유스케이스다.
   - 도메인 이벤트는 같은 트랜잭션에서 outbox에 기록한다. Kafka로 직접 보내지 않는다.
   - Command와 반환값에는 도메인 타입을 쓰지 않는다.
5. 진입점을 만든다.
   - REST: `ui.web`의 `<명사>CommandController`, `<동사><명사>Request`. 중복되면 안 되는 요청이면 `Idempotency-Key`를 필수로 받는다.
   - 이벤트: `ui.event`의 `<이벤트>Listener`. `common-messaging` 멱등 처리를 쓰고, 발행자의 JSON 예시로 역직렬화 테스트를 쓴다.
6. 권한: 경로 접두어(`/v1/seller`, `/v1/admin`)와 유스케이스 안의 소유권 검사.
7. 새 이벤트를 발행하면 JSON 예시 `src/test/resources/events/<type>.json`을 추가한다.
8. 스키마가 바뀌면 Flyway 마이그레이션을 추가하고, 저장소 통합 테스트(Testcontainers)를 쓴다.

## 쿼리 (조회)
1. `query.dao` 통합 테스트(Testcontainers)부터 쓴다. 기대하는 View를 검증한다.
2. `<명사>QueryDao`: QueryDSL `Projections.constructor`로 View를 바로 만든다. 엔티티를 로드한 뒤 변환하지 않는다.
   - Q-타입만 쓰고 엔티티 클래스는 참조하지 않는다.
   - enum 컬럼은 `stringValue()`로 넘기고, View가 `query.dto`의 조회용 enum으로 바꾼다.
3. `<명사>QueryService`, `<명사>View`, `<명사>QueryController`를 만든다.
4. 목록은 커서 페이징이 기본이다. N+1과 인덱스를 확인한다(실행 계획).

## 마무리
- 테스트, ArchUnit, 포매터가 모두 통과하는지 확인한다.
- REST API가 바뀌면 서비스 테스트가 실패한다(`OpenApiSnapshot`). `OPENAPI_UPDATE=true ./gradlew :core:<service>:test`으로 문서를 고치고 `clients/`에서 `pnpm --filter @openmarket/api-client generate`로 타입을 다시 만들어 함께 커밋한다.
- 문서와 달라진 점이 있으면 같은 변경에서 문서를 고친다.
