# 0023. 감사 로그와 DLT 메시지는 admin BFF가 서비스마다 물어 합치고, 운영 지표는 업무 DB에서 센다
- 상태: 승인
- 날짜: 2026-10-26

## 맥락
M6 관리자 백오피스는 세 화면을 더한다(ADM-4, ADM-5).
- 감사 로그: `audit_log`는 서비스 스키마마다 있다([data.md](../architecture/data.md) "audit_log"). 지금 쓰는 서비스는 member, catalog, payment, fulfillment, promotion, review, settlement이고, DLT 재처리를 더하면 리스너가 있는 서비스는 모두 쓴다.
- DLT 메시지: 서비스마다 자기 `dead_letter` 테이블에 있다([ADR 0022](0022-dead-letter-reprocessing.md)).
- 운영 지표: 일별 주문 수, 거래액(GMV), 결제 실패율. 주문은 order, 결제는 payment가 갖는다.

서비스는 다른 스키마를 읽지 않는다([ADR 0002](0002-single-postgres-schema-per-service.md)). Next.js 서버가 BFF다([ADR 0006](0006-nextjs-bff.md)).

## 결정
- 서비스마다 같은 모양의 관리자 조회 API를 둔다. `common-web`이 `/v1/admin/audit-logs`를, `common-messaging`이 `/v1/admin/dead-letters`를 연다. 서비스 코드는 더할 것이 없다.
- 목록은 시간 내림차순 커서 방식이다. 커서는 `(시각, id)`이고 서비스는 커서보다 앞선 행을 `size`개까지 준다. admin BFF는 대상 서비스에 동시에 묻고 결과를 시간순으로 합쳐 앞의 `size`개를 보여 준다. 각 서비스가 `size`개씩 주므로 합친 앞 `size`개는 전체에서도 맞다. 다음 페이지 커서는 보여 준 마지막 행이다.
- 대상 서비스 목록은 admin 앱 설정에 둔다. 응답하지 않는 서비스가 있으면 나머지로 보여 주고 빠진 서비스를 화면에 알린다. 한 서비스 장애로 화면 전체가 실패하지 않게 한다(NFR-4).
- 거르기는 기간, 서비스, 작업한 사람, 작업(감사 로그), 상태·토픽(DLT 메시지)이다. 서비스로 거르면 그 서비스에만 묻는다.
- 운영 지표는 Prometheus가 아니라 업무 DB에서 센다. 화면에 보여 주는 값은 주문·결제 기록과 맞아야 하고, Prometheus는 보관 기간이 7일이며 재시작하면 카운터가 처음부터 다시 센다.
  - order: `GET /v1/admin/metrics/orders?from&to` → 날짜(KST)별 주문 수, 결제된 주문 수, GMV
  - payment: `GET /v1/admin/metrics/payments?from&to` → 날짜(KST)별 승인 시도 결과 수와 실패율
  - 정의는 [order.md](../architecture/order.md) "운영 지표", [payment.md](../architecture/payment.md) "운영 지표"에 있다. 기간은 31일까지다.
- 감사 로그에는 개인정보가 없다(쓸 때 넣지 않는다). DLT 메시지 응답은 ADR 0022대로 페이로드를 주지 않는다.

## 결과
- 좋아지는 점
  - 새 서비스, 새 토픽, 다른 스키마 읽기 없이 화면을 만든다. 공통 API는 라이브러리 하나를 고치면 모든 서비스에 같게 들어간다.
  - 지표가 업무 기록과 같아서 화면의 숫자를 주문·결제 목록으로 맞춰 볼 수 있다.
- 감수하는 점
  - 목록 한 페이지마다 대상 서비스 수만큼 요청한다(core 9개와 auth, file, notification. gateway 레이트 리밋은 사용자마다 초당 20개라 한 쪽 12개는 들어간다). 관리자 화면은 사용자가 적어 받아들인다.
  - 전체 건수와 페이지 번호가 없다. 커서로 다음 페이지만 간다.
  - 서비스를 더하면 admin 앱 설정의 대상 목록도 고쳐야 한다. 감사 로그 조회 PR에서 `add-service` skill에 이 단계를 더한다.
  - 지표를 그때그때 센다. 기간을 31일로 막고 인덱스를 둔다. 부하 테스트에서 느리면 일별 집계 테이블을 둔다.

## 검토한 대안
- **감사 로그를 이벤트로 한 곳에 모으기**: 조회가 단순하지만 감사 로그마다 outbox 이벤트와 저장할 서비스가 필요하다. 받을 서비스가 정해져 있지 않다.
- **로그(Loki)로 감사 로그 남기기**: 보관 기간이 7일이고 지우거나 고칠 수 없다는 보장이 없다. NFR-8에 맞지 않는다.
- **운영 지표를 Prometheus 지표로**: Grafana 대시보드(NFR-10)는 이 방식으로 따로 만든다. 다만 관리자 화면의 업무 숫자로는 위 이유로 쓰지 않는다.
