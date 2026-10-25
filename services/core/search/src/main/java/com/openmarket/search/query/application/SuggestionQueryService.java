package com.openmarket.search.query.application;

import com.openmarket.search.query.dao.SuggestionQueryDao;
import com.openmarket.search.query.dto.SuggestionListView;
import java.util.List;
import org.springframework.stereotype.Service;

/** 검색어 자동완성(SRC-2). 공백뿐인 검색어는 빈 목록이다. */
@Service
public class SuggestionQueryService {

    /** 자동완성 최대 개수. */
    static final int LIMIT = 10;

    private final SuggestionQueryDao dao;

    public SuggestionQueryService(SuggestionQueryDao dao) {
        this.dao = dao;
    }

    public SuggestionListView suggest(String q) {
        if (q.isBlank()) {
            return new SuggestionListView(List.of());
        }
        return new SuggestionListView(dao.suggest(q.strip(), LIMIT));
    }
}
