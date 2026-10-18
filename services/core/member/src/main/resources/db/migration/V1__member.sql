-- 회원 프로필과 배송지(ACC-3). id는 auth의 account id와 같다(공유 식별자).
CREATE TABLE member (
    id                 uuid         PRIMARY KEY,
    email              varchar(320) NOT NULL,
    name               varchar(50)  NOT NULL,
    phone              varchar(13),
    email_verified_at  timestamptz,
    -- 기본 배송지. 배송지의 표시가 아니라 여기 하나만 둬서 둘이 될 수 없다. 외래 키는 아래 address를 만든 뒤 건다.
    default_address_id uuid,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    version            bigint       NOT NULL
);

CREATE TABLE address (
    id              uuid         PRIMARY KEY,
    member_id       uuid         NOT NULL REFERENCES member (id),
    label           varchar(20),
    recipient_name  varchar(50)  NOT NULL,
    recipient_phone varchar(13)  NOT NULL,
    zip_code        varchar(5)   NOT NULL,
    base_address    varchar(200) NOT NULL,
    detail_address  varchar(200),
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL
);

CREATE INDEX address_member_id_idx ON address (member_id);

-- 기본 배송지를 지우는 트랜잭션은 회원의 기본 배송지를 바꾼 뒤 배송지를 지운다. 순서에 상관없이 커밋 때 확인한다.
ALTER TABLE member
    ADD CONSTRAINT member_default_address_fk
        FOREIGN KEY (default_address_id) REFERENCES address (id) DEFERRABLE INITIALLY DEFERRED;
