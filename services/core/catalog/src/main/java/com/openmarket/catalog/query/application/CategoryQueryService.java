package com.openmarket.catalog.query.application;

import com.openmarket.catalog.query.dao.CategoryQueryDao;
import com.openmarket.catalog.query.dto.CategoryView;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리 트리 조회(공개). */
@Service
public class CategoryQueryService {

    private final CategoryQueryDao dao;

    public CategoryQueryService(CategoryQueryDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> tree() {
        return dao.findTree();
    }
}
