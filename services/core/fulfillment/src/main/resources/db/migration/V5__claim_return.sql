-- 반품·교환 신청, 판매자 승인·거부(docs/architecture/fulfillment.md "반품·교환 신청", "판매자 승인과 거부").
ALTER TABLE claim
    -- 구매자 설명. 발송 전 취소는 비어 있다
    ADD COLUMN description      varchar(500),
    -- 반품 승인 때 판매자가 고른 재입고 여부
    ADD COLUMN restock          boolean,
    ADD COLUMN rejection_reason varchar(500),
    ADD COLUMN rejected_at      timestamptz;

-- 클레임 사진(file 서비스의 파일 ID). 신청 때 정해지고 바뀌지 않는다.
CREATE TABLE claim_file (
    claim_id uuid    NOT NULL REFERENCES claim (id),
    position integer NOT NULL,
    file_id  uuid    NOT NULL,
    PRIMARY KEY (claim_id, position)
);
