-- 리뷰 작성 자격(docs/architecture/review.md "리뷰 작성 자격"). 구매확정한 주문 항목마다 하나다.
CREATE TABLE review_eligibility (
    order_line_id   uuid         PRIMARY KEY,
    order_id        uuid         NOT NULL,
    seller_order_id uuid         NOT NULL,
    product_id      uuid         NOT NULL,
    product_name    varchar(100) NOT NULL,
    option_value_1  varchar(30),
    option_value_2  varchar(30),
    seller_id       uuid         NOT NULL,
    buyer_id        uuid         NOT NULL,
    confirmed_at    timestamptz  NOT NULL,
    review_id       uuid,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL
);
-- 구매자의 쓸 수 있는 리뷰(리뷰 ID가 없는 자격), 구매확정 최근 순
CREATE INDEX review_eligibility_buyer_idx
    ON review_eligibility (buyer_id, confirmed_at DESC, order_line_id) WHERE review_id IS NULL;

-- 리뷰(docs/architecture/review.md "리뷰"). 주문 항목마다 하나이고 고치거나 지우지 않는다.
CREATE TABLE review (
    id              uuid          PRIMARY KEY,
    order_line_id   uuid          NOT NULL UNIQUE,
    order_id        uuid          NOT NULL,
    seller_order_id uuid          NOT NULL,
    product_id      uuid          NOT NULL,
    product_name    varchar(100)  NOT NULL,
    option_value_1  varchar(30),
    option_value_2  varchar(30),
    seller_id       uuid          NOT NULL,
    buyer_id        uuid          NOT NULL,
    rating          integer       NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content         varchar(1000) NOT NULL,
    status          varchar(16)   NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL
);
-- 상품 리뷰 목록(최근 순), 내 리뷰, 판매자 리뷰
CREATE INDEX review_product_idx ON review (product_id, created_at DESC, id) WHERE status = 'VISIBLE';
CREATE INDEX review_buyer_idx ON review (buyer_id, created_at DESC, id);
CREATE INDEX review_seller_idx ON review (seller_id, created_at DESC, id);

-- 리뷰 사진. file 서비스의 파일 ID와 넣은 순서
CREATE TABLE review_file (
    review_id uuid    NOT NULL REFERENCES review (id),
    position  integer NOT NULL,
    file_id   uuid    NOT NULL,
    PRIMARY KEY (review_id, position)
);

-- 상품 별점 요약(docs/architecture/review.md "별점 요약"). 보이는 리뷰만 센다.
CREATE TABLE rating_summary (
    product_id   uuid        PRIMARY KEY,
    rating_count bigint      NOT NULL CHECK (rating_count >= 0),
    rating_sum   bigint      NOT NULL CHECK (rating_sum >= 0),
    revision     bigint      NOT NULL,
    updated_at   timestamptz NOT NULL
);
