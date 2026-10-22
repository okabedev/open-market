-- 클레임 재입고(docs/architecture/catalog.md "클레임 재입고"). 재고 조정 이력에 클레임 ID와 함께 남긴다.
ALTER TABLE stock_adjustment ADD COLUMN claim_id uuid;
-- 클레임과 SKU마다 이력이 하나다. 같은 클레임이 다시 와도 두 번 더하지 않는다.
CREATE UNIQUE INDEX stock_adjustment_claim_idx ON stock_adjustment (claim_id, sku_id)
    WHERE claim_id IS NOT NULL;
