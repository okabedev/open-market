-- 선착순 발급(docs/architecture/promotion.md "선착순 발급"). 발급 수는 쿠폰 행의 조건부 UPDATE로 올린다.
-- Redis가 앞에서 거르고, 수량과 1인 1매는 이 테이블의 제약이 마지막으로 지킨다(R11).
ALTER TABLE coupon ADD COLUMN issued_count integer NOT NULL DEFAULT 0;
ALTER TABLE coupon ADD CONSTRAINT coupon_issued_count_check CHECK (issued_count BETWEEN 0 AND quantity);

-- 발급 쿠폰(docs/architecture/promotion.md "발급 쿠폰"). 만료는 상태가 아니라 expires_at으로 정한다.
CREATE TABLE issued_coupon (
    id         uuid        PRIMARY KEY,
    coupon_id  uuid        NOT NULL REFERENCES coupon (id),
    member_id  uuid        NOT NULL,
    status     varchar(20) NOT NULL,
    -- 결제 전 주문에 묶였거나 쓰였을 때 그 주문
    order_id   uuid,
    issued_at  timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    version    bigint      NOT NULL,
    CHECK ((status = 'AVAILABLE') = (order_id IS NULL))
);
-- 쿠폰 정책과 회원의 쌍은 하나다(1인 1매). 결제 전 취소로 복원해도 새 행을 만들지 않는다.
CREATE UNIQUE INDEX issued_coupon_member_idx ON issued_coupon (coupon_id, member_id);
-- 내 쿠폰 목록(최근 발급 순)
CREATE INDEX issued_coupon_owner_idx ON issued_coupon (member_id, issued_at);
