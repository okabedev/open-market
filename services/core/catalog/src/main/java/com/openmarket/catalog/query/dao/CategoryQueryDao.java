package com.openmarket.catalog.query.dao;

import static com.openmarket.catalog.command.domain.QCategory.category;

import com.openmarket.catalog.query.dto.CategoryRefView;
import com.openmarket.catalog.query.dto.CategoryView;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryQueryDao {

    private final JPAQueryFactory query;

    public CategoryQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 대분류와 그 소분류. 각각 표시 순서대로다. 카테고리는 수십 개라 한 번에 읽는다. */
    public List<CategoryView> findTree() {
        List<Tuple> rows =
                query.select(category.id, category.parentId, category.name)
                        .from(category)
                        .orderBy(category.displayOrder.asc(), category.name.asc())
                        .fetch();
        Map<UUID, List<CategoryView>> children = new LinkedHashMap<>();
        for (Tuple row : rows) {
            @Nullable UUID parentId = row.get(category.parentId);
            if (parentId != null) {
                children.computeIfAbsent(parentId, id -> new ArrayList<>())
                        .add(
                                new CategoryView(
                                        Objects.requireNonNull(row.get(category.id)),
                                        Objects.requireNonNull(row.get(category.name)),
                                        List.of()));
            }
        }
        return rows.stream()
                .filter(row -> row.get(category.parentId) == null)
                .map(
                        row -> {
                            UUID id = Objects.requireNonNull(row.get(category.id));
                            return new CategoryView(
                                    id,
                                    Objects.requireNonNull(row.get(category.name)),
                                    List.copyOf(children.getOrDefault(id, List.of())));
                        })
                .toList();
    }

    /** 대분류부터 이 카테고리까지. 없는 카테고리면 비어 있다. */
    public List<CategoryRefView> findPath(UUID categoryId) {
        List<CategoryRefView> path = new ArrayList<>();
        @Nullable UUID current = categoryId;
        while (current != null) {
            Tuple row =
                    query.select(category.id, category.name, category.parentId)
                            .from(category)
                            .where(category.id.eq(current))
                            .fetchOne();
            if (row == null) {
                break;
            }
            path.add(
                    new CategoryRefView(
                            Objects.requireNonNull(row.get(category.id)),
                            Objects.requireNonNull(row.get(category.name))));
            current = row.get(category.parentId);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }
}
