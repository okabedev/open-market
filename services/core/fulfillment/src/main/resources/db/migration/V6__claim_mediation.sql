-- 중재(docs/architecture/fulfillment.md "중재"). 판매자의 거부 사유와 관리자의 거부 확정 사유를 따로 둔다.
ALTER TABLE claim
    ADD COLUMN mediation_requested_at timestamptz,
    ADD COLUMN mediation_reason       varchar(500),
    ADD COLUMN mediation_decided_at   timestamptz;
-- 관리자 목록이 중재 중인 클레임을 중재 요청이 오래된 순으로 찾는다.
CREATE INDEX claim_mediating_idx ON claim (mediation_requested_at) WHERE status = 'MEDIATING';
