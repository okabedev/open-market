# 0003. Transactional Outbox + Debezium CDC
- 상태: 승인
- 날짜: 2026-10-16

## 맥락
DB 커밋과 Kafka 발행을 따로 하면, 둘 중 하나만 성공했을 때 이벤트가 유실되거나 없는 변경이 발행된다(NFR-1).

## 결정
- 유스케이스는 도메인 변경과 `outbox_event` INSERT를 한 트랜잭션으로 커밋한다.
- Debezium Postgres 커넥터 하나가 모든 서비스 스키마의 `outbox_event`를 논리 복제로 읽고, Outbox Event Router로 `aggregate_type` 토픽에 발행한다.
- 이벤트는 JSON으로 직렬화하고 스키마 레지스트리는 쓰지 않는다. 형식 호환은 발행자 JSON 예시와 소비자 테스트로 검증한다.
- 소비자는 `processed_event` 테이블로 멱등 처리한다.

## 결과
- 좋아지는 점: 발행 유실이 없다. 애플리케이션은 Kafka 장애와 상관없이 커밋할 수 있다. 폴링 지연이 없다.
- 감수하는 점: Debezium Connect를 운영해야 한다. 복제 슬롯 관리가 필요하다. at-least-once라 소비자 멱등성이 필수다.
- 확인할 점: [risks.md](../architecture/risks.md) R5.

## 검토한 대안
- 스케줄러 폴링 발행: 구성은 단순하지만 지연과 DB 부하가 있고, 여러 인스턴스에서 중복 발행을 막아야 한다.
- Avro + 스키마 레지스트리: 형식 검증은 강하지만 구성 요소가 늘고, 로컬 메모리와 학습 부담이 크다.
