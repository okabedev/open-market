# 0027. 서비스마다 마이그레이션 계정(owner)과 앱 계정(app)을 나눈다
- 상태: 승인
- 날짜: 2026-10-29

## 맥락
서비스마다 계정 `<service>_app` 하나가 스키마 소유자였고, Flyway와 앱이 같은 계정을 썼다([data.md](../architecture/data.md)). M6 보안 점검에서 감사 로그(`audit_log`)와 결제 원장(`payment_ledger_entry`)을 UPDATE·DELETE·TRUNCATE를 거절하는 트리거로 추가 전용으로 만들었다. 하지만 소유자는 트리거를 지울 수 있어, 앱 계정을 가진 코드(SQL 주입, 잘못 쓴 정리 작업)가 기록을 지울 길이 남았다. 그때는 바꿀 곳이 많아 받아들였다([security.md](../architecture/security.md) "받아들인 위험", 2026-10-29). M6 이후 백로그로 다시 본다.

앱은 실행 중에 DDL을 쓰지 않는다. 테이블은 모두 Flyway가 만들고(`ddl-auto=validate`), 공통 라이브러리(outbox, 멱등 기록, ShedLock, 감사 로그, DLT)도 데이터만 읽고 쓴다.

## 결정
- 서비스마다 두 계정을 둔다.
  - `<service>_owner`: 스키마 소유자. Flyway만 쓴다(`spring.flyway.user`, 비밀번호 `<SERVICE>_DB_OWNER_PASSWORD`).
  - `<service>_app`: 앱 연결(Hikari). 스키마 `USAGE`, 테이블 `SELECT`·`INSERT`·`UPDATE`·`DELETE`, 시퀀스 `USAGE`·`SELECT`만 갖는다. owner가 나중에 만드는 객체에도 같은 권한이 가도록 `ALTER DEFAULT PRIVILEGES FOR ROLE <service>_owner`를 둔다.
- 계정과 권한은 `infra/postgres/init/01-service-schemas.sh`가 만든다. 계정 이름은 공통 `application.yml`이 서비스 이름으로 정하고, `<service>.yml`에는 두 비밀번호 자리 표시자만 둔다.
- 감사 로그·결제 원장의 트리거는 그대로 둔다. 이제 앱 계정은 트리거를 지울 수 없고(소유자가 아님), 트리거가 UPDATE·DELETE를 거절한다. TRUNCATE 권한은 처음부터 없다.
- 테스트(Testcontainers)는 계정 하나(컨테이너 관리자)로 그대로 돈다. 권한 분리는 빈 볼륨으로 띄우는 E2E·부하·장애 주입 스택이 확인한다.

## 결과
- 좋아지는 점: 앱 계정으로는 스키마를 바꾸거나 추가 전용 기록을 지울 수 없다. 앱 계정 비밀번호가 새도 피해가 데이터 읽기·쓰기로 줄어든다.
- 감수하는 점: `.env` 비밀번호가 서비스마다 하나씩(12개) 늘고, 이미 있는 로컬 DB는 볼륨을 지우고(`docker compose down -v`) 다시 만들어야 한다. Flyway가 기동할 때 연결을 하나 더 잠깐 쓴다.
- 후속 작업: 운영 배치에서는 owner 계정을 배포 단계(마이그레이션 잡)만 갖고 앱 컨테이너에는 주지 않는다. 지금은 로컬 실행이라 앱 컨테이너가 기동하며 Flyway를 돌린다.

## 검토한 대안
- **owner를 NOLOGIN으로 두고 앱 계정이 `SET ROLE`로 마이그레이션**: 앱 계정이 그 역할의 구성원이 되므로 앱 코드도 `SET ROLE`로 소유자가 될 수 있다. 막는 것이 없다.
- **트리거 대신 앱 계정에서 감사 테이블 UPDATE·DELETE 권한만 회수**: 소유자는 권한과 상관없이 무엇이든 할 수 있어, 계정을 나누지 않으면 효과가 없다.
- **서비스 컨테이너 밖의 마이그레이션 잡(Flyway CLI 컨테이너)**: 앱 컨테이너에 owner 비밀번호가 가지 않아 더 낫지만, 서비스마다 Compose 서비스와 기동 순서가 하나씩 늘어난다. 로컬 실행에서는 얻는 것보다 복잡하다.
