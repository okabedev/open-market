-- 판매 중지 사유와 관리자 제재(docs/architecture/catalog.md "판매 상태").
ALTER TABLE product
    ADD COLUMN stop_reason varchar(32),
    ADD COLUMN ban_reason  varchar(500),
    ADD COLUMN banned_at   timestamptz;
-- 판매자 정지·해제가 그 판매자의 상품을 모두 잠근다.
CREATE INDEX product_seller_idx ON product (seller_id);
-- 관리자 상품 목록(상태별, 최근 수정 순)
CREATE INDEX product_status_updated_idx ON product (status, updated_at DESC);
