package com.openmarket.search.ui.web;

import com.openmarket.search.query.application.SuggestionQueryService;
import com.openmarket.search.query.dto.SuggestionListView;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 검색어 자동완성. 토큰 없이 연다(docs/architecture/search.md "자동완성 API"). */
@RestController
@RequestMapping("/v1/suggestions")
public class SuggestionQueryController {

    private final SuggestionQueryService suggestions;

    public SuggestionQueryController(SuggestionQueryService suggestions) {
        this.suggestions = suggestions;
    }

    /** 검색어(1~50자)로 시작하는 단어가 든 판매 중 상품명을 판매량 많은 순으로 최대 10개 준다. */
    @GetMapping
    public SuggestionListView suggest(@RequestParam @Size(min = 1, max = 50) String q) {
        return suggestions.suggest(q);
    }
}
