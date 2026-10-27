package com.openmarket.catalog.query.application;

import com.openmarket.catalog.query.dao.CategoryQueryDao;
import com.openmarket.catalog.query.dto.CategoryView;
import java.util.List;
import org.springframework.stereotype.Service;

/** 카테고리 트리 조회(공개). */
@Service
public class CategoryQueryService {

    private final CategoryQueryDao dao;

    public CategoryQueryService(CategoryQueryDao dao) {
        this.dao = dao;
    }

    /** 메모리에 둔 트리라 트랜잭션(DB 연결)을 열지 않는다. */
    public List<CategoryView> tree() {
        return dao.findTree();
    }
}
