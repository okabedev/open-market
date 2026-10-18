-- 가입·이메일 인증·로그인 잠금(ACC-1).
ALTER TABLE account
    ADD COLUMN failed_login_count integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until       timestamptz;

-- 이메일 인증 토큰. 원문은 저장하지 않고 SHA-256 해시(hex)만 둔다.
CREATE TABLE email_verification (
    id         uuid        PRIMARY KEY,
    account_id uuid        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX email_verification_token_hash_uk ON email_verification (token_hash);
CREATE INDEX email_verification_account_idx ON email_verification (account_id);
