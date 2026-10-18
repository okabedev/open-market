-- 계정: 로그인 수단과 역할(docs/glossary.md "계정"). 프로필 개인정보는 member가 갖는다.
-- id는 accountId(= memberId)이고 토큰의 sub가 된다. email은 소문자로 정규화해 저장한다.
CREATE TABLE account (
    id                uuid         PRIMARY KEY,
    email             varchar(320) NOT NULL,
    password_hash     varchar(200) NOT NULL,
    status            varchar(20)  NOT NULL,
    email_verified_at timestamptz,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    version           bigint       NOT NULL
);
CREATE UNIQUE INDEX account_email_uk ON account (email);

CREATE TABLE account_role (
    account_id uuid        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    role       varchar(20) NOT NULL,
    PRIMARY KEY (account_id, role)
);
