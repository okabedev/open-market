-- 쿠폰 정책(docs/architecture/promotion.md "쿠폰 정책"). 만든 뒤 조건을 바꾸지 않고 발급 중지만 한다.
CREATE TABLE coupon (
    id               uuid        PRIMARY KEY,
    issuer           varchar(20) NOT NULL,
    -- 판매자 쿠폰만. 플랫폼 쿠폰은 비어 있다
    seller_id        uuid,
    name             varchar(50) NOT NULL,
    discount_type    varchar(20) NOT NULL,
    -- 정액은 원, 정률은 %
    discount_value   bigint      NOT NULL CHECK (discount_value >= 1),
    -- 정률의 최대 할인(원). 없으면 상한이 없다
    max_discount     bigint      CHECK (max_discount >= 1),
    min_order_amount bigint      NOT NULL CHECK (min_order_amount >= 0),
    quantity         integer     NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
    issue_start_at   timestamptz NOT NULL,
    issue_end_at     timestamptz NOT NULL,
    -- 발급 쿠폰의 사용 기한(발급 후 일수)
    valid_days       integer     NOT NULL CHECK (valid_days BETWEEN 1 AND 365),
    status           varchar(20) NOT NULL,
    stopped_at       timestamptz,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    version          bigint      NOT NULL,
    CHECK ((issuer = 'PLATFORM') = (seller_id IS NULL)),
    CHECK (discount_type = 'RATE' OR max_discount IS NULL),
    CHECK (discount_type = 'FIXED' OR discount_value <= 100),
    CHECK (issue_end_at > issue_start_at)
);
-- 판매자 쿠폰 목록(최근 순), 공개 목록(판매자별 발급 중)
CREATE INDEX coupon_seller_idx ON coupon (seller_id, created_at);
CREATE INDEX coupon_issuing_idx ON coupon (issuer, issue_end_at) WHERE status = 'ISSUING';
