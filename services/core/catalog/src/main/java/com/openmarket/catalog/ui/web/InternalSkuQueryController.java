package com.openmarket.catalog.ui.web;

import com.openmarket.catalog.query.application.SkuQueryService;
import com.openmarket.catalog.query.dto.SkuSummaryView;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 간 조회. {@code internal} scope 토큰만 부른다(common-security). gateway는 {@code /internal/**}을 넘기지
 * 않는다.
 */
@RestController
public class InternalSkuQueryController {

    private final SkuQueryService skus;

    public InternalSkuQueryController(SkuQueryService skus) {
        this.skus = skus;
    }

    /** SKU 일괄 조회. {@code ids}는 쉼표로 구분한 1~100개. 없는 SKU는 빠진다. */
    @GetMapping("/internal/v1/skus")
    public List<SkuSummaryView> skus(@RequestParam(defaultValue = "") List<UUID> ids) {
        return skus.summaries(ids);
    }
}
