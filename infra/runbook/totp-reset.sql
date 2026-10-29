-- 관리자 2단계 인증(TOTP) 등록 해제. docs/runbook.md "관리자 TOTP 등록 해제".
-- 기기를 잃었거나 복구 코드를 다 쓴 관리자가 다음 로그인에 다시 등록하게 한다. 한 트랜잭션이고, 다시 실행해도 결과가 같다.
--   email  대상 계정 이메일(대소문자 무관)
--   actor  처리한 사람(관리자 계정 ID). 감사 로그 actor_id
--   reason 사유. 감사 로그 detail. 개인정보를 적지 않는다
-- 실행(레포 루트):
--   docker compose exec -T postgres psql -U postgres -d openmarket \
--     -v email=admin@openmarket.local -v actor=<관리자 계정 ID> -v reason='기기 분실' < infra/runbook/totp-reset.sql
\set ON_ERROR_STOP on

BEGIN;

-- 계정이 없으면 \gset이 실패하고 아무것도 바꾸지 않고 끝난다.
SELECT id AS account_id FROM auth.account WHERE email = lower(:'email') \gset

-- 감사 로그(NFR-8). 등록이 남아 있을 때만 쓴다.
INSERT INTO auth.audit_log (id, actor_id, action, target_type, target_id, detail, occurred_at)
SELECT uuidv7(), :'actor', 'TOTP_ENROLLMENT_RESET', 'ACCOUNT', :'account_id', :'reason', now()
WHERE EXISTS (SELECT 1 FROM auth.totp_credential WHERE account_id = :'account_id');

-- 복구 코드(totp_recovery_code)는 외래 키로 함께 지워진다.
DELETE FROM auth.totp_credential WHERE account_id = :'account_id';

-- 발급한 인가 코드·토큰을 지운다. 리프레시 토큰 갱신이 실패해 다시 로그인하고, 그때 새로 등록한다.
DELETE FROM auth.oauth2_authorization WHERE principal_name = :'account_id';

COMMIT;
