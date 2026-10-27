package com.openmarket.catalog.query.dao;

import static org.assertj.core.api.Assertions.assertThat;

import com.openmarket.catalog.query.dao.CategoryQueryDao.Snapshot;
import com.openmarket.catalog.query.dao.CategoryQueryDao.Snapshot.Row;
import com.openmarket.catalog.query.dto.CategoryRefView;
import com.openmarket.catalog.query.dto.CategoryView;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryQueryDaoSnapshotTest {

    static final UUID FASHION = UUID.randomUUID();
    static final UUID DIGITAL = UUID.randomUUID();
    static final UUID TOPS = UUID.randomUUID();
    static final UUID PANTS = UUID.randomUUID();
    static final UUID PHONES = UUID.randomUUID();

    /** 표시 순서대로 받은 행. 소분류가 대분류보다 먼저 와도 된다. */
    static final Snapshot SNAPSHOT =
            Snapshot.of(
                    List.of(
                            new Row(TOPS, FASHION, "상의"),
                            new Row(FASHION, null, "패션"),
                            new Row(PHONES, DIGITAL, "휴대폰"),
                            new Row(DIGITAL, null, "디지털"),
                            new Row(PANTS, FASHION, "하의")));

    @Test
    @DisplayName("트리는 대분류와 그 소분류를 받은 순서대로 둔다")
    void treeKeepsOrder() {
        assertThat(SNAPSHOT.tree())
                .containsExactly(
                        new CategoryView(
                                FASHION,
                                "패션",
                                List.of(
                                        new CategoryView(TOPS, "상의", List.of()),
                                        new CategoryView(PANTS, "하의", List.of()))),
                        new CategoryView(
                                DIGITAL,
                                "디지털",
                                List.of(new CategoryView(PHONES, "휴대폰", List.of()))));
    }

    @Test
    @DisplayName("경로는 대분류부터 그 카테고리까지다")
    void pathFromRoot() {
        assertThat(SNAPSHOT.path(PANTS))
                .containsExactly(
                        new CategoryRefView(FASHION, "패션"), new CategoryRefView(PANTS, "하의"));
        assertThat(SNAPSHOT.path(DIGITAL)).containsExactly(new CategoryRefView(DIGITAL, "디지털"));
    }

    @Test
    @DisplayName("없는 카테고리의 경로는 비어 있다")
    void unknownCategoryHasEmptyPath() {
        assertThat(SNAPSHOT.path(UUID.randomUUID())).isEmpty();
    }
}
