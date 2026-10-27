package com.openmarket.catalog.query.dao;

import static com.openmarket.catalog.command.domain.QCategory.category;

import com.openmarket.catalog.query.dto.CategoryRefView;
import com.openmarket.catalog.query.dto.CategoryView;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * 카테고리 조회. 카테고리는 수십 개이고 마이그레이션으로만 바뀌므로(docs/architecture/catalog.md) 처음 쓸 때 한 번 읽어 두고, 트리와 상품 상세의
 * 경로를 DB 없이 만든다. 바뀐 카테고리는 다시 기동하면(마이그레이션 적용) 보인다.
 */
@Repository
public class CategoryQueryDao {

    private final JPAQueryFactory query;
    private volatile @Nullable Snapshot snapshot;

    public CategoryQueryDao(JPAQueryFactory query) {
        this.query = query;
    }

    /** 대분류와 그 소분류. 각각 표시 순서대로다. */
    public List<CategoryView> findTree() {
        return snapshot().tree();
    }

    /** 대분류부터 이 카테고리까지. 없는 카테고리면 비어 있다. */
    public List<CategoryRefView> findPath(UUID categoryId) {
        return snapshot().path(categoryId);
    }

    private Snapshot snapshot() {
        @Nullable Snapshot loaded = snapshot;
        if (loaded == null) {
            // 동시에 처음 불려 두 번 읽어도 결과가 같다.
            loaded = load();
            snapshot = loaded;
        }
        return loaded;
    }

    private Snapshot load() {
        return Snapshot.of(
                query
                        .select(category.id, category.parentId, category.name)
                        .from(category)
                        .orderBy(category.displayOrder.asc(), category.name.asc())
                        .fetch()
                        .stream()
                        .map(
                                row ->
                                        new Snapshot.Row(
                                                Objects.requireNonNull(row.get(category.id)),
                                                row.get(category.parentId),
                                                Objects.requireNonNull(row.get(category.name))))
                        .toList());
    }

    /** 한 번 읽은 카테고리 전체. 트리와 경로를 DB 없이 만든다. 카테고리는 마이그레이션으로만 바뀐다(docs/architecture/catalog.md). */
    static final class Snapshot {

        /** 카테고리 한 행. 표시 순서대로 받는다. */
        record Row(UUID id, @Nullable UUID parentId, String name) {}

        private final List<CategoryView> tree;
        private final Map<UUID, List<CategoryRefView>> paths;

        private Snapshot(List<CategoryView> tree, Map<UUID, List<CategoryRefView>> paths) {
            this.tree = tree;
            this.paths = paths;
        }

        static Snapshot of(List<Row> rows) {
            Map<UUID, Row> byId = new HashMap<>();
            Map<UUID, List<CategoryView>> children = new LinkedHashMap<>();
            for (Row row : rows) {
                byId.put(row.id(), row);
                if (row.parentId() != null) {
                    children.computeIfAbsent(row.parentId(), id -> new ArrayList<>())
                            .add(new CategoryView(row.id(), row.name(), List.of()));
                }
            }
            List<CategoryView> tree =
                    rows.stream()
                            .filter(row -> row.parentId() == null)
                            .map(
                                    row ->
                                            new CategoryView(
                                                    row.id(),
                                                    row.name(),
                                                    List.copyOf(
                                                            children.getOrDefault(
                                                                    row.id(), List.of()))))
                            .toList();
            Map<UUID, List<CategoryRefView>> paths = new HashMap<>();
            for (Row row : rows) {
                List<CategoryRefView> path = new ArrayList<>();
                @Nullable Row current = row;
                // 부모를 따라 올라간다. 순환이 있는 잘못된 데이터에서 멈추도록 행 수만큼만 오른다.
                for (int depth = 0; current != null && depth < rows.size(); depth++) {
                    path.addFirst(new CategoryRefView(current.id(), current.name()));
                    current = current.parentId() == null ? null : byId.get(current.parentId());
                }
                paths.put(row.id(), List.copyOf(path));
            }
            return new Snapshot(tree, Map.copyOf(paths));
        }

        List<CategoryView> tree() {
            return tree;
        }

        /** 대분류부터 이 카테고리까지. 없는 카테고리면 비어 있다. */
        List<CategoryRefView> path(UUID categoryId) {
            return paths.getOrDefault(categoryId, List.of());
        }
    }
}
