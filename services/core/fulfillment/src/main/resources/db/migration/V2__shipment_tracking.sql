-- 발주 확인, 발송(송장), 배송 시뮬레이터(docs/architecture/fulfillment.md "발주 확인과 발송", "배송 시뮬레이터").
ALTER TABLE shipment
    ADD COLUMN carrier         varchar(20),
    ADD COLUMN tracking_number varchar(14),
    ADD COLUMN accepted_at     timestamptz,
    ADD COLUMN shipped_at      timestamptz,
    ADD COLUMN in_transit_at   timestamptz,
    ADD COLUMN delivered_at    timestamptz;
-- 배송 시뮬레이터가 단계마다 기준 시각이 오래된 배송을 찾는다.
CREATE INDEX shipment_shipped_idx ON shipment (shipped_at) WHERE status = 'SHIPPED';
CREATE INDEX shipment_in_transit_idx ON shipment (in_transit_at) WHERE status = 'IN_TRANSIT';
