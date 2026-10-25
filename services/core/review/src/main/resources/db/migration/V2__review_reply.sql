-- 답글(docs/architecture/review.md "답글"). 리뷰마다 하나이고 판매자가 고칠 수 있다.
CREATE TABLE review_reply (
    review_id  uuid          PRIMARY KEY REFERENCES review (id),
    seller_id  uuid          NOT NULL,
    content    varchar(1000) NOT NULL,
    created_at timestamptz   NOT NULL,
    updated_at timestamptz   NOT NULL,
    version    bigint        NOT NULL
);

-- 관리자 리뷰 목록(최근 순, 상태와 상관없이)
CREATE INDEX review_created_idx ON review (created_at DESC, id);
