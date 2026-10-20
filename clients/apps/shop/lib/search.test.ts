import { describe, expect, it } from "vitest";
import { searchHref, searchQuery } from "./search";

const CATEGORY = "0199f000-0000-7000-8000-000000000101";

describe("searchQuery", () => {
  it("검색 조건을 읽어 search API 조건으로 바꾼다", () => {
    expect(
      searchQuery({
        q: "  반팔 티셔츠 ",
        categoryId: CATEGORY,
        minPrice: "10000",
        maxPrice: "50000",
        sort: "PRICE_ASC",
        cursor: "abc",
      }),
    ).toEqual({
      q: "반팔 티셔츠",
      categoryId: CATEGORY,
      minPrice: 10_000,
      maxPrice: 50_000,
      sort: "PRICE_ASC",
      cursor: "abc",
    });
  });

  it("빈 값과 틀린 값은 버리고, 정렬은 관련도가 기본이다", () => {
    expect(
      searchQuery({
        q: " ",
        categoryId: "not-uuid",
        minPrice: "-1",
        maxPrice: "abc",
        sort: "POPULAR",
        cursor: ["a", "b"],
      }),
    ).toEqual({ sort: "RELEVANCE" });
  });

  it("검색어는 100자까지만 쓴다", () => {
    expect(searchQuery({ q: "가".repeat(150) }).q).toHaveLength(100);
  });
});

describe("searchHref", () => {
  const current = searchQuery({ q: "티셔츠", categoryId: CATEGORY, sort: "LATEST", cursor: "c1" });

  it("조건을 바꾸면 커서를 버린다(첫 쪽부터)", () => {
    expect(searchHref(current, { sort: "PRICE_DESC" })).toBe(
      `/products?q=%ED%8B%B0%EC%85%94%EC%B8%A0&categoryId=${CATEGORY}&sort=PRICE_DESC`,
    );
  });

  it("다음 쪽은 같은 조건에 커서만 바꾼다", () => {
    expect(searchHref(current, { cursor: "c2" })).toBe(
      `/products?q=%ED%8B%B0%EC%85%94%EC%B8%A0&categoryId=${CATEGORY}&sort=LATEST&cursor=c2`,
    );
  });

  it("값을 undefined로 주면 그 조건을 뺀다. 기본 정렬은 적지 않는다", () => {
    expect(searchHref(current, { categoryId: undefined, sort: "RELEVANCE" })).toBe(
      "/products?q=%ED%8B%B0%EC%85%94%EC%B8%A0",
    );
  });
});
