-- 관리자 2단계 인증(TOTP, M6). docs/architecture/auth.md "관리자 2단계 인증".
-- 비밀 값은 AES-256-GCM 암호문(키 AUTH_TOTP_SECRET_KEY)이다. 등록 화면을 열면 PENDING, 코드를 맞히면 ACTIVE.
CREATE TABLE totp_credential (
    account_id     uuid         PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    secret         varchar(200) NOT NULL,
    status         varchar(20)  NOT NULL,
    last_used_step bigint,
    enrolled_at    timestamptz,
    updated_at     timestamptz  NOT NULL,
    version        bigint       NOT NULL
);

-- 복구 코드. 원문은 등록할 때 한 번 보여 주고 해시만 둔다. 쓰면 used_at을 채운다.
CREATE TABLE totp_recovery_code (
    account_id uuid         NOT NULL REFERENCES totp_credential (account_id) ON DELETE CASCADE,
    code_hash  varchar(100) NOT NULL,
    used_at    timestamptz
);
CREATE INDEX totp_recovery_code_account_idx ON totp_recovery_code (account_id);
